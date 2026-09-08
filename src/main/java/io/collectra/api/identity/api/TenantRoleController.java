package io.collectra.api.identity.api;

import io.collectra.api.identity.application.RbacService;
import io.collectra.api.identity.domain.Role;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/roles")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class TenantRoleController {
    private final RbacService rbac;

    public TenantRoleController(RbacService rbac) {
        this.rbac = rbac;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('ROLE_READ')")
    List<RoleResponse> roles() {
        return rbac.roles(TenantContext.requireTenantId()).stream()
                .map(this::response)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('ROLE_CREATE')")
    RoleResponse create(@Valid @RequestBody CreateRoleRequest request) {
        return response(
                rbac.createRole(
                        TenantContext.requireTenantId(), request.code(), request.permissions()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('ROLE_UPDATE')")
    RoleResponse update(@PathVariable UUID id, @Valid @RequestBody CreateRoleRequest request) {
        return response(rbac.updateRole(TenantContext.requireTenantId(), id, request.code(),
                request.permissions()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('ROLE_UPDATE')")
    void delete(@PathVariable UUID id) {
        rbac.deleteRole(TenantContext.requireTenantId(), id);
    }

    private RoleResponse response(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getScopeType(),
                role.isSystemRole(), rbac.rolePermissionCodes(role.getId()));
    }

    record RoleResponse(UUID id, String code, String scope, boolean system,
            List<String> permissions) {}

    record CreateRoleRequest(
            @Pattern(regexp = "[A-Z][A-Z0-9_]{2,79}") String code,
            @NotEmpty Set<String> permissions) {}
}
