package io.collectra.api.identity.api;

import io.collectra.api.identity.application.RbacService;
import io.collectra.api.identity.domain.Role;
import io.collectra.api.identity.domain.TenantMembership;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity")
public class IdentityController {
    private final TenantMembershipRepository memberships;
    private final RbacService rbac;

    public IdentityController(TenantMembershipRepository memberships, RbacService rbac) {
        this.memberships = memberships; this.rbac = rbac;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_READ')")
    List<MembershipResponse> users() {
        return memberships.findAllByTenantId(TenantContext.require()).stream()
                .map(m -> new MembershipResponse(m.getId(), m.getUserId(), m.getStatus())).toList();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    List<RoleResponse> roles() {
        return rbac.roles(TenantContext.require()).stream().map(RoleResponse::from).toList();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    RoleResponse createRole(@Valid @RequestBody CreateRoleRequest request) {
        return RoleResponse.from(rbac.createRole(TenantContext.require(), request.code(), request.permissions()));
    }

    @PutMapping("/memberships/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    void assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest request) {
        rbac.assignRoles(TenantContext.require(), id, request.roleIds());
    }

    @PatchMapping("/memberships/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_BLOCK')")
    void changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        rbac.changeMembershipStatus(TenantContext.require(), id, request.active());
    }

    record MembershipResponse(UUID id, UUID userId, String status) {}
    record RoleResponse(UUID id, String code, String scope, boolean system) {
        static RoleResponse from(Role role) { return new RoleResponse(role.getId(), role.getCode(), role.getScopeType(), role.isSystemRole()); }
    }
    record CreateRoleRequest(@Pattern(regexp = "[A-Z][A-Z0-9_]{2,79}") String code,
            @NotEmpty Set<String> permissions) {}
    record AssignRolesRequest(@NotEmpty Set<UUID> roleIds) {}
    record StatusRequest(boolean active) {}
}
