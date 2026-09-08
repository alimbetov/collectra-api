package io.collectra.api.identity.api;

import io.collectra.api.identity.domain.Permission;
import io.collectra.api.identity.infrastructure.PermissionRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/permissions")
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('ROLE_READ')")
public class TenantPermissionController {
    private final PermissionRepository permissions;

    public TenantPermissionController(PermissionRepository permissions) {
        this.permissions = permissions;
    }

    @GetMapping
    List<PermissionResponse> list() {
        return permissions.findAllByOrderByModuleAscCodeAsc().stream()
                .map(PermissionResponse::from).toList();
    }

    record PermissionResponse(String code, String module, String description) {
        static PermissionResponse from(Permission permission) {
            return new PermissionResponse(permission.getCode(), permission.getModule(),
                    permission.getDescription());
        }
    }
}
