package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RbacSeedIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void systemRolesHaveExpectedScopeAndPermissionBundles() {
        assertThat(jdbc.queryForObject("select count(*) from roles where system_role", Integer.class))
                .isEqualTo(3);
        assertThat(scopeOf("PLATFORM_SUPER_ADMIN")).isEqualTo("PLATFORM");
        assertThat(scopeOf("TENANT_ADMIN")).isEqualTo("TENANT");
        assertThat(scopeOf("TENANT_USER")).isEqualTo("TENANT");
        assertThat(permissionCount("TENANT_ADMIN")).isEqualTo(18);
        assertThat(permissionCount("TENANT_USER")).isEqualTo(3);
    }

    private String scopeOf(String role) {
        return jdbc.queryForObject("select scope_type from roles where code = ?", String.class, role);
    }

    private int permissionCount(String role) {
        return jdbc.queryForObject("""
                select count(*)
                  from role_permissions rp join roles r on r.id = rp.role_id
                 where r.code = ?
                """, Integer.class, role);
    }
}
