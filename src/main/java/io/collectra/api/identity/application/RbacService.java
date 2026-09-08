package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.*;
import io.collectra.api.identity.infrastructure.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacService {
    public static final UUID TENANT_ADMIN_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID TENANT_USER_ROLE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final TenantMembershipRepository memberships;
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;

    public RbacService(RoleRepository roles, PermissionRepository permissions,
            TenantMembershipRepository memberships, UserAccountRepository users, JdbcTemplate jdbc) {
        this.roles = roles; this.permissions = permissions; this.memberships = memberships;
        this.users = users; this.jdbc = jdbc;
    }

    public List<String> permissionCodes(UUID membershipId) { return roles.findPermissionCodes(membershipId); }
    public List<String> roleCodes(UUID membershipId) { return roles.findRoleCodes(membershipId); }

    @Transactional
    public void assignSystemRole(UUID membershipId, UUID roleId) {
        jdbc.update("insert into membership_roles(membership_id, role_id) values (?, ?) on conflict do nothing",
                membershipId, roleId);
    }

    @Transactional
    public Role createRole(UUID tenantId, String code, Set<String> permissionCodes) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (roles.existsByTenantIdAndCodeIgnoreCase(tenantId, normalized))
            throw new IllegalArgumentException("Role code already exists");
        List<Permission> selected = permissions.findAllByCodeIn(permissionCodes);
        if (selected.size() != permissionCodes.size()) throw new IllegalArgumentException("Unknown permission code");
        Role role = roles.save(new Role(tenantId, normalized));
        selected.forEach(permission -> jdbc.update(
                "insert into role_permissions(role_id, permission_id) values (?, ?)", role.getId(), permission.getId()));
        return role;
    }

    @Transactional
    public void assignRoles(UUID tenantId, UUID membershipId, Set<UUID> roleIds) {
        TenantMembership membership = memberships.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Membership not found"));
        for (UUID roleId : roleIds) {
            Role role = roles.findById(roleId).orElseThrow(() -> new NoSuchElementException("Role not found"));
            if (!"TENANT".equals(role.getScopeType()))
                throw new IllegalArgumentException("Only tenant roles can be assigned to a membership");
            if (role.getTenantId() != null && !tenantId.equals(role.getTenantId()))
                throw new IllegalArgumentException("Cross-tenant role assignment is forbidden");
        }
        jdbc.update("delete from membership_roles where membership_id = ?", membershipId);
        roleIds.forEach(roleId -> jdbc.update(
                "insert into membership_roles(membership_id, role_id) values (?, ?)", membershipId, roleId));
        users.findById(membership.getUserId()).orElseThrow().authorizationChanged();
    }

    public List<Role> roles(UUID tenantId) { return roles.findAvailableTenantRoles(tenantId); }

    @Transactional
    public void changeMembershipStatus(UUID tenantId, UUID membershipId, boolean active) {
        TenantMembership membership = memberships.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Membership not found"));
        if (active) membership.activate(); else membership.block();
        UserAccount user = users.findById(membership.getUserId()).orElseThrow();
        user.authorizationChanged();
    }
}
