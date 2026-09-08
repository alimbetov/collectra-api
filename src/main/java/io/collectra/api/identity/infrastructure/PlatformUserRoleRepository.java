package io.collectra.api.identity.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformUserRoleRepository {
    private static final UUID SUPER_ADMIN_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbc;

    public PlatformUserRoleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasSuperAdminRole(UUID userId) {
        Boolean result = jdbc.queryForObject(
                "select exists(select 1 from platform_user_roles where user_id = ? and role_id = ?)",
                Boolean.class, userId, SUPER_ADMIN_ROLE);
        return Boolean.TRUE.equals(result);
    }

    public boolean activeSuperAdminExists() {
        Boolean result = jdbc.queryForObject("""
                select exists(
                    select 1
                      from platform_user_roles pur
                      join user_accounts ua on ua.id = pur.user_id
                     where pur.role_id = ? and ua.status = 'ACTIVE'
                )
                """, Boolean.class, SUPER_ADMIN_ROLE);
        return Boolean.TRUE.equals(result);
    }

    public void assignSuperAdmin(UUID userId) {
        jdbc.update("insert into platform_user_roles(user_id, role_id) values (?, ?) on conflict do nothing",
                userId, SUPER_ADMIN_ROLE);
    }

    public List<String> roleCodes(UUID userId) {
        return jdbc.queryForList("""
                select r.code
                  from platform_user_roles pur
                  join roles r on r.id = pur.role_id
                 where pur.user_id = ? and r.scope_type = 'PLATFORM'
                """, String.class, userId);
    }
}
