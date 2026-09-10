package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.Permission;
import io.collectra.api.identity.domain.Role;
import io.collectra.api.identity.domain.TenantMembership;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.PermissionRepository;
import io.collectra.api.identity.infrastructure.RoleRepository;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacService {
    public static final UUID TENANT_ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID TENANT_USER_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final TenantMembershipRepository memberships;
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;

    public RbacService(
            RoleRepository roles,
            PermissionRepository permissions,
            TenantMembershipRepository memberships,
            UserAccountRepository users,
            JdbcTemplate jdbc) {
        this.roles = roles;
        this.permissions = permissions;
        this.memberships = memberships;
        this.users = users;
        this.jdbc = jdbc;
    }

    public List<String> permissionCodes(UUID membershipId) {
        return roles.findPermissionCodes(membershipId);
    }

    public List<String> roleCodes(UUID membershipId) {
        return roles.findRoleCodes(membershipId);
    }

    @Transactional(readOnly = true)
    public List<TenantMembership> memberships(UUID tenantId) {
        return memberships.findAllByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Permission> permissions() {
        return permissions.findAllByOrderByModuleAscCodeAsc();
    }

    @Transactional
    public void assignSystemRole(UUID membershipId, UUID roleId) {
        jdbc.update(
                "insert into membership_roles(membership_id, role_id) values (?, ?) on conflict do nothing",
                membershipId,
                roleId);
    }

    @Transactional
    public Role createRole(UUID tenantId, String code, Set<String> permissionCodes) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (roles.existsByTenantIdAndCodeIgnoreCase(tenantId, normalized)) {
            throw new IllegalArgumentException("Role code already exists");
        }
        List<Permission> selected = permissions.findAllByCodeIn(permissionCodes);
        if (selected.size() != permissionCodes.size()) {
            throw new IllegalArgumentException("Unknown permission code");
        }
        Role role = roles.saveAndFlush(new Role(tenantId, normalized));
        selected.forEach(
                permission ->
                        jdbc.update(
                                "insert into role_permissions(role_id, permission_id) values (?, ?)",
                                role.getId(),
                                permission.getId()));
        return role;
    }

    @Transactional
    public void assignRoles(UUID tenantId, UUID membershipId, Set<UUID> roleIds) {
        TenantMembership membership =
                memberships
                        .findByIdAndTenantId(membershipId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Membership not found"));
        if (roles.hasTenantAdminRole(membershipId)
                && !roleIds.contains(TENANT_ADMIN_ROLE)
                && roles.countActiveTenantAdmins(tenantId) <= 1) {
            throw new IllegalArgumentException(
                    "The last active tenant administrator cannot be demoted");
        }
        for (UUID roleId : roleIds) {
            Role role =
                    roles.findById(roleId)
                            .orElseThrow(() -> new NoSuchElementException("Role not found"));
            if (!"TENANT".equals(role.getScopeType())) {
                throw new IllegalArgumentException(
                        "Only tenant roles can be assigned to a membership");
            }
            if (role.getTenantId() != null && !tenantId.equals(role.getTenantId())) {
                throw new IllegalArgumentException("Cross-tenant role assignment is forbidden");
            }
        }
        jdbc.update("delete from membership_roles where membership_id = ?", membershipId);
        roleIds.forEach(
                roleId ->
                        jdbc.update(
                                "insert into membership_roles(membership_id, role_id) values (?, ?)",
                                membershipId,
                                roleId));
        users.findById(membership.getUserId()).orElseThrow().authorizationChanged();
    }

    public List<Role> roles(UUID tenantId) {
        return roles.findAvailableTenantRoles(tenantId);
    }

    public List<String> rolePermissionCodes(UUID roleId) {
        return jdbc.queryForList(
                """
                select p.code from role_permissions rp
                join permissions p on p.id = rp.permission_id
                where rp.role_id = ? order by p.code
                """,
                String.class,
                roleId);
    }

    @Transactional
    public Role updateRole(UUID tenantId, UUID roleId, String code, Set<String> permissionCodes) {
        Role role =
                roles.findByIdAndTenantId(roleId, tenantId)
                        .filter(value -> !value.isSystemRole())
                        .orElseThrow(() -> new NoSuchElementException("Custom role not found"));
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!role.getCode().equalsIgnoreCase(normalized)
                && roles.existsByTenantIdAndCodeIgnoreCase(tenantId, normalized)) {
            throw new IllegalArgumentException("Role code already exists");
        }
        List<Permission> selected = permissions.findAllByCodeIn(permissionCodes);
        if (selected.size() != permissionCodes.size()) {
            throw new IllegalArgumentException("Unknown permission code");
        }
        role.rename(normalized);
        jdbc.update("delete from role_permissions where role_id = ?", roleId);
        selected.forEach(
                permission ->
                        jdbc.update(
                                "insert into role_permissions(role_id, permission_id) values (?, ?)",
                                roleId,
                                permission.getId()));
        invalidateUsersWithRole(roleId);
        return role;
    }

    @Transactional
    public void deleteRole(UUID tenantId, UUID roleId) {
        Role role =
                roles.findByIdAndTenantId(roleId, tenantId)
                        .filter(value -> !value.isSystemRole())
                        .orElseThrow(() -> new NoSuchElementException("Custom role not found"));
        Integer assignments =
                jdbc.queryForObject(
                        "select count(*) from membership_roles where role_id = ?",
                        Integer.class,
                        roleId);
        if (assignments != null && assignments > 0) {
            throw new IllegalArgumentException("Role is assigned to memberships");
        }
        roles.delete(role);
    }

    private void invalidateUsersWithRole(UUID roleId) {
        jdbc.update(
                """
                update user_accounts ua set authorization_version = authorization_version + 1
                 where exists (
                    select 1 from tenant_memberships tm
                    join membership_roles mr on mr.membership_id = tm.id
                    where tm.user_id = ua.id and mr.role_id = ?
                 )
                """,
                roleId);
    }

    @Transactional
    public void changeMembershipStatus(UUID tenantId, UUID membershipId, boolean active) {
        TenantMembership membership =
                memberships
                        .findByIdAndTenantId(membershipId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Membership not found"));
        if (!active
                && membership.active()
                && roles.hasTenantAdminRole(membershipId)
                && roles.countActiveTenantAdmins(tenantId) <= 1) {
            throw new IllegalArgumentException(
                    "The last active tenant administrator cannot be blocked");
        }
        if (active) {
            membership.activate();
        } else {
            membership.block();
        }
        UserAccount user = users.findById(membership.getUserId()).orElseThrow();
        user.authorizationChanged();
    }
}
