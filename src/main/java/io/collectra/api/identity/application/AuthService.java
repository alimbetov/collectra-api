package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.*;
import io.collectra.api.identity.infrastructure.*;
import io.collectra.api.audit.application.SecurityAuditService;
import io.collectra.api.shared.error.InvalidRefreshTokenException;
import io.collectra.api.shared.outbox.OutboxService;
import io.collectra.api.shared.security.InMemoryRateLimiter;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final TenantMembershipRepository memberships;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RbacService rbac;
    private final OutboxService outbox;
    private final InMemoryRateLimiter limiter;
    private final SecurityAuditService audit;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(TenantRepository tenants, UserAccountRepository users,
            TenantMembershipRepository memberships, RefreshSessionRepository sessions,
            PasswordEncoder passwords, JwtService jwt, RbacService rbac, OutboxService outbox,
            InMemoryRateLimiter limiter, SecurityAuditService audit,
            @Value("${collectra.security.refresh-token-ttl:30d}") Duration refreshTtl) {
        this.tenants = tenants; this.users = users; this.memberships = memberships;
        this.sessions = sessions; this.passwords = passwords; this.jwt = jwt; this.rbac = rbac;
        this.outbox = outbox; this.refreshTtl = refreshTtl;
        this.limiter = limiter; this.audit = audit;
    }

    @Transactional
    public AuthTokens register(String slug, String company, String email, String password) {
        if (tenants.existsBySlugIgnoreCase(slug)) throw new IllegalArgumentException("Tenant slug already exists");
        Tenant tenant = tenants.save(new Tenant(slug.toLowerCase(Locale.ROOT), company));
        UserAccount user = users.save(new UserAccount(tenant.getId(), email, passwords.encode(password), SystemRole.TENANT_ADMIN));
        TenantMembership membership = memberships.saveAndFlush(new TenantMembership(tenant.getId(), user.getId()));
        rbac.assignSystemRole(membership.getId(), RbacService.TENANT_ADMIN_ROLE);
        outbox.append(tenant.getId(), "Tenant", tenant.getId(), "TenantRegistered", "{\"tenantId\":\"" + tenant.getId() + "\"}");
        audit.append(tenant.getId(), "USER", user.getId(), "TENANT_REGISTERED", "SUCCEEDED", null);
        return issue(user, membership, UUID.randomUUID(), null);
    }

    @Transactional
    public AuthTokens login(UUID tenantId, String email, String password) {
        limiter.check("login:" + tenantId + ":" + email.toLowerCase(Locale.ROOT), 5, Duration.ofMinutes(15));
        UserAccount user = users.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                .filter(account -> "ACTIVE".equals(account.getStatus()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwords.matches(password, user.getPasswordHash())) throw new BadCredentialsException("Invalid credentials");
        TenantMembership membership = memberships.findByTenantIdAndUserId(tenantId, user.getId())
                .filter(TenantMembership::active)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        AuthTokens result = issue(user, membership, UUID.randomUUID(), null);
        audit.append(tenantId, "USER", user.getId(), "LOGIN", "SUCCEEDED", null);
        return result;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens refresh(String rawToken) {
        RefreshSession old = sessions.findByTokenHashForUpdate(hash(rawToken)).orElseThrow(InvalidRefreshTokenException::new);
        if (!old.active()) {
            if (old.revoked()) {
                old.markReuseDetected();
                sessions.findAllByFamilyId(old.getFamilyId()).forEach(RefreshSession::revoke);
                audit.append(old.getTenantId(), "USER", old.getUserId(), "REFRESH_TOKEN_REUSE_DETECTED", "DENIED", "TOKEN_REUSE");
            }
            throw new InvalidRefreshTokenException();
        }
        UserAccount user = users.findById(old.getUserId()).filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(InvalidRefreshTokenException::new);
        TenantMembership membership = memberships.findByIdAndTenantId(old.getMembershipId(), old.getTenantId())
                .filter(TenantMembership::active).orElseThrow(InvalidRefreshTokenException::new);
        return issue(user, membership, old.getFamilyId(), old);
    }

    @Transactional
    public void logout(String rawToken) { sessions.findByTokenHash(hash(rawToken)).ifPresent(RefreshSession::revoke); }

    @Transactional
    public void logoutAll(UUID userId) {
        sessions.revokeAllByUserId(userId);
        audit.append(null, "USER", userId, "LOGOUT_ALL", "SUCCEEDED", null);
    }

    @Transactional
    public AuthTokens issueActivated(UserAccount user, TenantMembership membership) {
        return issue(user, membership, UUID.randomUUID(), null);
    }

    private AuthTokens issue(UserAccount user, TenantMembership membership, UUID family, RefreshSession predecessor) {
        byte[] bytes = new byte[48]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshSession session = sessions.save(new RefreshSession(user.getId(), membership.getTenantId(),
                membership.getId(), hash(raw), family, Instant.now().plus(refreshTtl)));
        if (predecessor != null) {
            predecessor.usedAt(Instant.now());
            predecessor.replaceWith(session.getId());
        }
        String access = jwt.issue(user, membership, rbac.roleCodes(membership.getId()),
                rbac.permissionCodes(membership.getId()));
        return new AuthTokens(access, raw, "Bearer", jwt.expiresIn());
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record AuthTokens(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
}
