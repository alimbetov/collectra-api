package io.collectra.api.identity.application;

import io.collectra.api.audit.application.SecurityAuditService;
import io.collectra.api.identity.domain.PasswordResetToken;
import io.collectra.api.identity.domain.RefreshSession;
import io.collectra.api.identity.domain.Role;
import io.collectra.api.identity.domain.SystemRole;
import io.collectra.api.identity.domain.TenantMembership;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.domain.UserInvitation;
import io.collectra.api.identity.infrastructure.PasswordResetTokenRepository;
import io.collectra.api.identity.infrastructure.RefreshSessionRepository;
import io.collectra.api.identity.infrastructure.RoleRepository;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.identity.infrastructure.UserInvitationRepository;
import io.collectra.api.shared.outbox.OutboxService;
import io.collectra.api.shared.security.InMemoryRateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserLifecycleService {
    private final UserInvitationRepository invitations;
    private final PasswordResetTokenRepository resetTokens;
    private final UserAccountRepository users;
    private final TenantMembershipRepository memberships;
    private final RoleRepository roles;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final RbacService rbac;
    private final AuthService auth;
    private final SecurityAuditService audit;
    private final OutboxService outbox;
    private final InMemoryRateLimiter limiter;
    private final boolean exposeTokens;
    private final SecureRandom random = new SecureRandom();

    public UserLifecycleService(
            UserInvitationRepository invitations,
            PasswordResetTokenRepository resetTokens,
            UserAccountRepository users,
            TenantMembershipRepository memberships,
            RoleRepository roles,
            RefreshSessionRepository sessions,
            PasswordEncoder passwords,
            RbacService rbac,
            AuthService auth,
            SecurityAuditService audit,
            OutboxService outbox,
            InMemoryRateLimiter limiter,
            @Value("${collectra.security.expose-development-tokens:false}") boolean exposeTokens) {
        this.invitations = invitations;
        this.resetTokens = resetTokens;
        this.users = users;
        this.memberships = memberships;
        this.roles = roles;
        this.sessions = sessions;
        this.passwords = passwords;
        this.rbac = rbac;
        this.auth = auth;
        this.audit = audit;
        this.outbox = outbox;
        this.limiter = limiter;
        this.exposeTokens = exposeTokens;
    }

    @Transactional
    public InvitationResponse invite(
            UUID tenantId, UUID invitedBy, String email, Set<UUID> requestedRoleIds) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByTenantIdAndEmailIgnoreCase(tenantId, normalized)) {
            throw new IllegalArgumentException("User already belongs to this tenant");
        }
        Set<UUID> roleIds =
                requestedRoleIds == null || requestedRoleIds.isEmpty()
                        ? Set.of(RbacService.TENANT_USER_ROLE)
                        : Set.copyOf(requestedRoleIds);
        validateRoles(tenantId, roleIds);
        invitations.findAllByTenantIdAndEmailIgnoreCaseAndStatus(tenantId, normalized, "PENDING")
                .forEach(UserInvitation::revoke);
        String raw = randomToken();
        UserInvitation invitation =
                invitations.save(
                        new UserInvitation(
                                tenantId,
                                normalized,
                                hash(raw),
                                Instant.now().plus(Duration.ofDays(7)),
                                invitedBy,
                                roleIds));
        audit.append(tenantId, "USER", invitedBy, "INVITATION_CREATED", "SUCCEEDED", null);
        outbox.append(
                tenantId,
                "UserInvitation",
                invitation.getId(),
                "UserInvitationCreated",
                "{\"invitationId\":\"" + invitation.getId() + "\"}");
        return invitationResponse(invitation, raw);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> invitations(UUID tenantId) {
        return invitations.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(value -> invitationResponse(value, null))
                .toList();
    }

    @Transactional
    public void revokeInvitation(UUID tenantId, UUID invitationId, UUID actorId) {
        UserInvitation invitation =
                invitations.findByIdAndTenantId(invitationId, tenantId).orElseThrow();
        invitation.revoke();
        audit.append(tenantId, "USER", actorId, "INVITATION_REVOKED", "SUCCEEDED", null);
    }

    @Transactional
    public AuthService.AuthTokens accept(
            String rawToken, String password, String displayName, String locale, String timezone) {
        Instant now = Instant.now();
        UserInvitation invitation =
                invitations.findByTokenHashForUpdate(hash(rawToken))
                        .filter(value -> value.activeAt(now))
                        .orElseThrow(() -> new BadCredentialsException("Invalid invitation"));
        if (users.existsByTenantIdAndEmailIgnoreCase(
                invitation.getTenantId(), invitation.getEmail())) {
            throw new IllegalArgumentException("User already belongs to this tenant");
        }
        UserAccount user =
                users.save(
                        new UserAccount(
                                invitation.getTenantId(),
                                invitation.getEmail(),
                                passwords.encode(password),
                                SystemRole.TENANT_USER));
        user.completeProfile(displayName, normalizeLocale(locale), normalizeTimezone(timezone));
        TenantMembership membership =
                memberships.saveAndFlush(
                        new TenantMembership(invitation.getTenantId(), user.getId()));
        rbac.assignRoles(invitation.getTenantId(), membership.getId(), invitation.getRoleIds());
        invitation.accept(now);
        audit.append(
                invitation.getTenantId(),
                "USER",
                user.getId(),
                "INVITATION_ACCEPTED",
                "SUCCEEDED",
                null);
        return auth.issueActivated(user, membership);
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId, UUID tenantId, UUID membershipId) {
        UserAccount user = users.findById(userId).orElseThrow();
        TenantMembership membership =
                memberships.findByIdAndTenantId(membershipId, tenantId)
                        .filter(value -> userId.equals(value.getUserId()))
                        .orElseThrow();
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getLocale(),
                user.getTimezone(),
                membership.getId(),
                membership.getStatus(),
                rbac.roleCodes(membershipId),
                rbac.permissionCodes(membershipId));
    }

    @Transactional
    public MeResponse updateMe(
            UUID userId,
            UUID tenantId,
            UUID membershipId,
            String displayName,
            String locale,
            String timezone) {
        UserAccount user = users.findById(userId).orElseThrow();
        user.updateProfile(displayName, normalizeLocale(locale), normalizeTimezone(timezone));
        return me(userId, tenantId, membershipId);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount user = users.findById(userId).orElseThrow();
        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        user.changePassword(passwords.encode(newPassword));
        sessions.revokeAllByUserId(userId);
        audit.append(user.getTenantId(), "USER", userId, "PASSWORD_CHANGED", "SUCCEEDED", null);
    }

    @Transactional
    public ResetRequestResponse forgotPassword(UUID tenantId, String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        limiter.check("password-reset:" + tenantId + ":" + normalized, 3, Duration.ofMinutes(30));
        UUID requestId = UUID.randomUUID();
        String raw = users.findByTenantIdAndEmailIgnoreCase(tenantId, normalized)
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .map(user -> {
                    String token = randomToken();
                    PasswordResetToken reset = resetTokens.save(
                            new PasswordResetToken(
                                    user.getId(), tenantId, hash(token), Instant.now().plus(Duration.ofMinutes(30))));
                    audit.append(tenantId, "USER", user.getId(), "PASSWORD_RESET_REQUESTED", "SUCCEEDED", null);
                    outbox.append(
                            tenantId,
                            "PasswordReset",
                            reset.getId(),
                            "PasswordResetRequested",
                            "{\"resetId\":\"" + reset.getId() + "\"}");
                    return token;
                })
                .orElse(null);
        return new ResetRequestResponse(requestId, exposeTokens ? raw : null);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken reset =
                resetTokens.findByTokenHashForUpdate(hash(rawToken))
                        .filter(value -> value.activeAt(now))
                        .orElseThrow(() -> new BadCredentialsException("Invalid reset token"));
        UserAccount user = users.findById(reset.getUserId()).orElseThrow();
        user.changePassword(passwords.encode(newPassword));
        reset.consume(now);
        sessions.revokeAllByUserId(user.getId());
        audit.append(reset.getTenantId(), "USER", user.getId(), "PASSWORD_RESET", "SUCCEEDED", null);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(UUID userId, UUID membershipId) {
        return sessions.findAllByUserIdAndMembershipIdOrderByCreatedAtDesc(userId, membershipId)
                .stream().map(SessionResponse::from).toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID membershipId, UUID sessionId) {
        RefreshSession session =
                sessions.findByIdAndUserIdAndMembershipId(sessionId, userId, membershipId)
                        .orElseThrow(NoSuchElementException::new);
        session.revoke();
    }

    private void validateRoles(UUID tenantId, Set<UUID> roleIds) {
        for (UUID roleId : roleIds) {
            Role role = roles.findById(roleId).orElseThrow();
            if (!"TENANT".equals(role.getScopeType())
                    || (role.getTenantId() != null && !tenantId.equals(role.getTenantId()))) {
                throw new IllegalArgumentException("Invalid invitation role");
            }
        }
    }

    private InvitationResponse invitationResponse(UserInvitation invitation, String rawToken) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getRoleIds(),
                exposeTokens ? rawToken : null);
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String normalizeLocale(String locale) {
        return locale == null || locale.isBlank() ? "ru" : locale.toLowerCase(Locale.ROOT);
    }

    private String normalizeTimezone(String timezone) {
        String value = timezone == null || timezone.isBlank() ? "Asia/Almaty" : timezone;
        java.time.ZoneId.of(value);
        return value;
    }

    public record InvitationResponse(
            UUID id,
            String email,
            String status,
            Instant expiresAt,
            Set<UUID> roleIds,
            String developmentToken) {}

    public record ResetRequestResponse(UUID requestId, String developmentToken) {}

    public record MeResponse(
            UUID id,
            String email,
            String displayName,
            String locale,
            String timezone,
            UUID membershipId,
            String membershipStatus,
            List<String> roles,
            List<String> permissions) {}

    public record SessionResponse(
            UUID id,
            Instant createdAt,
            Instant expiresAt,
            Instant lastUsedAt,
            Instant revokedAt,
            String userAgent,
            String sourceIp) {
        static SessionResponse from(RefreshSession session) {
            return new SessionResponse(
                    session.getId(),
                    session.getCreatedAt(),
                    session.getExpiresAt(),
                    session.getLastUsedAt(),
                    session.getRevokedAt(),
                    session.getUserAgent(),
                    session.getSourceIp());
        }
    }
}
