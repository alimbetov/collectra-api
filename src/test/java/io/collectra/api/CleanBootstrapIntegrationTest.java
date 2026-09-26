package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CleanBootstrapIntegrationTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void cleanPostgresBootstrapAppliesEveryPreVc9MigrationExactlyOnce() {
        Integer failed =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM databasechangelog WHERE exectype = 'FAILED'",
                        Integer.class);
        assertThat(failed).isZero();

        Integer businessCorePermissionMigration =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM databasechangelog
                        WHERE filename LIKE '%051-business-core-permissions.sql'
                        """,
                        Integer.class);
        assertThat(businessCorePermissionMigration).isOne();

        List<String> missingTables =
                jdbc.queryForList(
                        """
                        SELECT required.table_name
                        FROM (VALUES
                          ('tenants'),
                          ('customers'),
                          ('contracts'),
                          ('invoices'),
                          ('payments'),
                          ('collection_cases'),
                          ('document_templates'),
                          ('campaigns'),
                          ('messages'),
                          ('outbox_events'),
                          ('integration_sources'),
                          ('import_batches'),
                          ('stored_file')
                        ) AS required(table_name)
                        WHERE NOT EXISTS (
                          SELECT 1
                          FROM information_schema.tables actual
                          WHERE actual.table_schema = 'public'
                            AND actual.table_name = required.table_name
                        )
                        ORDER BY required.table_name
                        """,
                        String.class);
        assertThat(missingTables).isEmpty();

        Integer businessCorePermissions =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM permissions
                        WHERE code IN (
                          'CUSTOMER_READ', 'CUSTOMER_MANAGE',
                          'CONTRACT_READ', 'CONTRACT_MANAGE',
                          'RECEIVABLE_READ', 'RECEIVABLE_MANAGE',
                          'COLLECTION_READ', 'COLLECTION_MANAGE'
                        )
                        """,
                        Integer.class);
        assertThat(businessCorePermissions).isEqualTo(8);
    }
}
