package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.*;
import io.collectra.api.identity.infrastructure.*;
import io.collectra.api.shared.error.InvalidRefreshTokenException;
import io.collectra.api.shared.outbox.OutboxService;
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
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final OutboxService outbox;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            TenantRepository tenants,
            UserAccountRepository users,
            RefreshSessionRepository sessions,
            PasswordEncoder passwords,
            JwtService jwt,
            OutboxService outbox,
            @Value("${collectra.security.refresh-token-ttl:30d}") Duration refreshTtl) {
        this.tenants = tenants;
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.jwt = jwt;
        this.outbox = outbox;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public AuthTokens register(String slug, String company, String email, String password) {
        if (tenants.existsBySlugIgnoreCase(slug))
            throw new IllegalArgumentException("Tenant slug already exists");
        Tenant tenant = tenants.save(new Tenant(slug.toLowerCase(), company));
        UserAccount user =
                users.save(
                        new UserAccount(
                                tenant.getId(),
                                email,
                                passwords.encode(password),
                                SystemRole.TENANT_ADMIN));
        outbox.append(
                tenant.getId(),
                "Tenant",
                tenant.getId(),
                "TenantRegistered",
                "{\"tenantId\":\"" + tenant.getId() + "\"}");
        return tokens(user, UUID.randomUUID());
    }

    @Transactional
    public AuthTokens login(UUID tenantId, String email, String password) {
        UserAccount user =
                users.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                        .filter(u -> "ACTIVE".equals(u.getStatus()))
                        .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwords.matches(password, user.getPasswordHash()))
            throw new BadCredentialsException("Invalid credentials");
        return tokens(user, UUID.randomUUID());
    }

    @Transactional
    public AuthTokens refresh(String raw) {
        RefreshSession old =
                sessions.findByTokenHash(hash(raw)).orElseThrow(InvalidRefreshTokenException::new);
        if (!old.active()) {
            if (old.revoked())
                sessions.findAllByFamilyId(old.getFamilyId()).forEach(RefreshSession::revoke);
            throw new InvalidRefreshTokenException();
        }
        old.revoke();
        UserAccount user =
                users.findById(old.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        return tokens(user, old.getFamilyId());
    }

    @Transactional
    public void logout(String raw) {
        sessions.findByTokenHash(hash(raw)).ifPresent(RefreshSession::revoke);
    }

    private AuthTokens tokens(UserAccount user, UUID family) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(
                new RefreshSession(
                        user.getId(),
                        user.getTenantId(),
                        hash(raw),
                        family,
                        Instant.now().plus(refreshTtl)));
        return new AuthTokens(jwt.issue(user), raw, "Bearer", jwt.expiresIn());
    }

    private String hash(String raw) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record AuthTokens(
            String accessToken, String refreshToken, String tokenType, long expiresIn) {}
}
