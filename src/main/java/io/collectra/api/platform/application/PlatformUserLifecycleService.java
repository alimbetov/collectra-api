package io.collectra.api.platform.application;

import io.collectra.api.audit.application.SecurityAuditService;
import io.collectra.api.identity.domain.TenantMembership;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.RefreshSessionRepository;
import io.collectra.api.identity.infrastructure.RoleRepository;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.shared.error.BusinessConflictException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserLifecycleService {
    private final TenantMembershipRepository memberships;
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final RefreshSessionRepository sessions;
    private final SecurityAuditService audit;

    public PlatformUserLifecycleService(
            TenantMembershipRepository memberships,
            UserAccountRepository users,
            RoleRepository roles,
            RefreshSessionRepository sessions,
            SecurityAuditService audit) {
        this.memberships = memberships;
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public TenantMembership changeMembershipStatus(
            UUID actorId,
            UUID membershipId,
            boolean active,
            long expectedRevision,
            String reason) {
        TenantMembership membership =
                memberships
                        .findByIdForUpdate(membershipId)
                        .orElseThrow(
                                () -> new java.util.NoSuchElementException("Membership not found"));

        if (membership.getVersion() != expectedRevision) {
            throw new BusinessConflictException(
                    "VERSION_CONFLICT",
                    "Membership revision conflict: expected "
                            + expectedRevision
                            + " but was "
                            + membership.getVersion());
        }

        UserAccount user =
                users.findById(membership.getUserId())
                        .orElseThrow(
                                () -> new java.util.NoSuchElementException("User account not found"));

        if (active) {
            if (membership.active()) {
                throw new BusinessConflictException(
                        "INVALID_MEMBERSHIP_STATUS_TRANSITION", "Membership is already active");
            }
            membership.activate();
            user.authorizationChanged();
            audit.appendTransactional(
                    membership.getTenantId(),
                    "PLATFORM_USER",
                    actorId,
                    "TENANT_MEMBERSHIP_ACTIVATED",
                    "SUCCEEDED",
                    reason);
            return membership;
        }

        if (!membership.active()) {
            throw new BusinessConflictException(
                    "INVALID_MEMBERSHIP_STATUS_TRANSITION", "Membership is already blocked");
        }

        if (roles.hasTenantAdminRole(membershipId)) {
            roles.lockTenantAdminRole();
            if (roles.countActiveTenantAdmins(membership.getTenantId()) <= 1) {
                throw new BusinessConflictException(
                        "LAST_ACTIVE_TENANT_ADMIN",
                        "The last active tenant administrator is protected");
            }
        }

        membership.block();
        user.authorizationChanged();
        sessions.revokeAllByMembershipId(membershipId);
        audit.appendTransactional(
                membership.getTenantId(),
                "PLATFORM_USER",
                actorId,
                "TENANT_MEMBERSHIP_BLOCKED",
                "SUCCEEDED",
                reason);
        return membership;
    }

    @Transactional
    public TenantMembership revokeAllSessions(UUID actorId, UUID membershipId, String reason) {
        TenantMembership membership =
                memberships
                        .findByIdForUpdate(membershipId)
                        .orElseThrow(
                                () -> new java.util.NoSuchElementException("Membership not found"));

        int revoked = sessions.revokeAllByMembershipId(membershipId);
        if (revoked > 0) {
            UserAccount user =
                    users.findById(membership.getUserId())
                            .orElseThrow(
                                    () ->
                                            new java.util.NoSuchElementException(
                                                    "User account not found"));
            user.authorizationChanged();
        }

        audit.appendTransactional(
                membership.getTenantId(),
                "PLATFORM_USER",
                actorId,
                "TENANT_MEMBERSHIP_SESSIONS_REVOKED",
                "SUCCEEDED",
                reason);
        return membership;
    }
}
