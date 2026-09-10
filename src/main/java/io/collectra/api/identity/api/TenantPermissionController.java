package io.collectra.api.identity.api;

import io.collectra.api.identity.application.RbacService;
import io.collectra.api.identity.domain.Permission;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/permissions")
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('ROLE_READ')")
public class TenantPermissionController {
    private final RbacService rbac;

    public TenantPermissionController(RbacService rbac) {
        this.rbac = rbac;
    }

    @GetMapping
    List<PermissionResponse> list() {
        return rbac.permissions().stream().map(PermissionResponse::from).toList();
    }

    record PermissionResponse(String code, String module, String description) {
        static PermissionResponse from(Permission permission) {
            return new PermissionResponse(
                    permission.getCode(), permission.getModule(), permission.getDescription());
        }
    }
}
