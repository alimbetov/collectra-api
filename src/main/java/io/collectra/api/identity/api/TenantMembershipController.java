package io.collectra.api.identity.api;

import io.collectra.api.identity.application.IdentityDirectoryService;
import io.collectra.api.identity.application.IdentityDirectoryService.UserSummary;
import io.collectra.api.identity.application.RbacService;
import io.collectra.api.identity.application.SessionAdministrationService;
import io.collectra.api.identity.domain.RefreshSession;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class TenantMembershipController {
    private final RbacService rbac;
    private final SessionAdministrationService sessionAdministration;
    private final IdentityDirectoryService identityDirectory;

    public TenantMembershipController(
            RbacService rbac,
            SessionAdministrationService sessionAdministration,
            IdentityDirectoryService identityDirectory) {
        this.rbac = rbac;
        this.sessionAdministration = sessionAdministration;
        this.identityDirectory = identityDirectory;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('USER_READ')")
    List<MembershipResponse> users() {
        UUID tenantId = TenantContext.requireTenantId();
        var memberships = rbac.memberships(tenantId);
        Map<UUID, UserSummary> usersById =
                identityDirectory.users(
                                tenantId,
                                memberships.stream().map(m -> m.getUserId()).toList())
                        .stream()
                        .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        return memberships.stream()
                .map(
                        membership -> {
                            UserSummary user = usersById.get(membership.getUserId());
                            return new MembershipResponse(
                                    membership.getId(),
                                    membership.getUserId(),
                                    user == null ? null : user.email(),
                                    user == null ? null : user.displayName(),
                                    membership.getStatus());
                        })
                .toList();
    }

    @GetMapping("/memberships/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('ROLE_READ')")
    List<UUID> roles(@PathVariable UUID id) {
        return rbac.roleIds(TenantContext.requireTenantId(), id);
    }

    @PutMapping("/memberships/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('ROLE_ASSIGN')")
    void assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest request) {
        rbac.assignRoles(TenantContext.requireTenantId(), id, request.roleIds());
    }

    @PatchMapping("/memberships/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('USER_BLOCK')")
    void changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        rbac.changeMembershipStatus(TenantContext.requireTenantId(), id, request.active());
    }

    @GetMapping("/memberships/{id}/sessions")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('USER_READ')")
    List<SessionResponse> sessions(@PathVariable UUID id) {
        return sessionAdministration.sessions(TenantContext.requireTenantId(), id).stream()
                .map(SessionResponse::from)
                .toList();
    }

    @DeleteMapping("/memberships/{id}/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('USER_UPDATE')")
    void revokeSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        sessionAdministration.revoke(TenantContext.requireTenantId(), id, sessionId);
    }

    @DeleteMapping("/memberships/{id}/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('USER_UPDATE')")
    void revokeSessions(@PathVariable UUID id) {
        sessionAdministration.revokeAll(TenantContext.requireTenantId(), id);
    }

    record MembershipResponse(
            UUID id, UUID userId, String email, String displayName, String status) {}

    record AssignRolesRequest(@NotEmpty Set<UUID> roleIds) {}

    record StatusRequest(boolean active) {}

    record SessionResponse(
            UUID id,
            java.time.Instant createdAt,
            java.time.Instant expiresAt,
            java.time.Instant lastUsedAt,
            java.time.Instant revokedAt,
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
