package io.collectra.api.identity.application;

import io.collectra.api.audit.application.SecurityAuditService;
import io.collectra.api.identity.domain.RefreshSession;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.PlatformUserRoleRepository;
import io.collectra.api.identity.infrastructure.RefreshSessionRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.shared.error.InvalidRefreshTokenException;
import io.collectra.api.shared.security.InMemoryRateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAuthService {
    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PlatformUserRoleRepository platformRoles;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final InMemoryRateLimiter limiter;
    private final SecurityAuditService audit;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public PlatformAuthService(UserAccountRepository users, RefreshSessionRepository sessions,
            PlatformUserRoleRepository platformRoles, PasswordEncoder passwords, JwtService jwt,
            InMemoryRateLimiter limiter, SecurityAuditService audit,
            @Value("${collectra.security.refresh-token-ttl:30d}") Duration refreshTtl) {
        this.users = users; this.sessions = sessions; this.platformRoles = platformRoles;
        this.passwords = passwords; this.jwt = jwt; this.limiter = limiter; this.audit = audit;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public PlatformTokens login(String email, String password) {
        String normalized = email.trim().toLowerCase();
        limiter.check("platform-login:" + normalized, 5, Duration.ofMinutes(15));
        UserAccount user = users.findByTenantIdIsNullAndEmailIgnoreCase(normalized)
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .filter(value -> platformRoles.hasSuperAdminRole(value.getId()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwords.matches(password, user.getPasswordHash()))
            throw new BadCredentialsException("Invalid credentials");
        PlatformTokens result = issue(user, UUID.randomUUID(), null);
        audit.append(null, "USER", user.getId(), "PLATFORM_LOGIN", "SUCCEEDED", null);
        return result;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public PlatformTokens refresh(String rawToken) {
        RefreshSession old = sessions.findByTokenHashForUpdate(hash(rawToken))
                .filter(session -> "PLATFORM".equals(session.getContextType()))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!old.active()) {
            if (old.revoked()) {
                old.markReuseDetected();
                sessions.findAllByFamilyId(old.getFamilyId()).forEach(RefreshSession::revoke);
            }
            throw new InvalidRefreshTokenException();
        }
        UserAccount user = users.findById(old.getUserId())
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .filter(value -> platformRoles.hasSuperAdminRole(value.getId()))
                .orElseThrow(InvalidRefreshTokenException::new);
        return issue(user, old.getFamilyId(), old);
    }

    @Transactional
    public void logout(String rawToken) {
        sessions.findByTokenHash(hash(rawToken))
                .filter(session -> "PLATFORM".equals(session.getContextType()))
                .ifPresent(RefreshSession::revoke);
    }

    @Transactional
    public void logoutAll(UUID userId) {
        sessions.revokeAllPlatformByUserId(userId);
    }

    private PlatformTokens issue(UserAccount user, UUID family, RefreshSession predecessor) {
        byte[] bytes = new byte[48]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshSession session = sessions.save(RefreshSession.platform(user.getId(), hash(raw), family,
                Instant.now().plus(refreshTtl)));
        if (predecessor != null) {
            predecessor.usedAt(Instant.now());
            predecessor.replaceWith(session.getId());
        }
        return new PlatformTokens(jwt.issuePlatform(user, platformRoles.roleCodes(user.getId())),
                raw, "Bearer", jwt.expiresIn());
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record PlatformTokens(String accessToken, String refreshToken, String tokenType,
            long expiresIn) {}
}
