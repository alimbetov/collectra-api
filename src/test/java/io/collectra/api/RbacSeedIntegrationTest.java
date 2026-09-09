package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

        assertThat(permissionCodes("TENANT_ADMIN"))
                .hasSize(31)
                .contains("FILE_READ", "FILE_UPLOAD", "FILE_DELETE", "FILE_ADMIN");

        assertThat(permissionCodes("TENANT_USER"))
                .hasSize(7)
                .contains("FILE_READ", "FILE_UPLOAD")
                .doesNotContain("FILE_DELETE", "FILE_ADMIN");
    }

    private String scopeOf(String role) {
        return jdbc.queryForObject("select scope_type from roles where code = ?", String.class, role);
    }

    private List<String> permissionCodes(String role) {
        return jdbc.queryForList(
                """
                select p.code
                  from role_permissions rp
                  join roles r on r.id = rp.role_id
                  join permissions p on p.id = rp.permission_id
                 where r.code = ?
                 order by p.code
                """,
                String.class,
                role);
    }
}
