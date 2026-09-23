package io.collectra.api.shared.partition;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PartitionMaintenanceIntegrationTest extends AbstractIntegrationTest {
    private static final String TABLE = "partition_maintenance_test";

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PartitionMaintenanceService service;
    @Autowired PartitionMaintenanceProperties properties;

    private PartitionMaintenanceProperties.TablePolicy policy;

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TABLE IF EXISTS public." + TABLE + " CASCADE");
        jdbc.execute(
                "CREATE TABLE public."
                        + TABLE
                        + " (id BIGINT, created_at TIMESTAMPTZ NOT NULL)"
                        + " PARTITION BY RANGE (created_at)");

        properties.setDaysAhead(2);
        properties.setRetentionYears(4);

        policy = new PartitionMaintenanceProperties.TablePolicy();
        policy.setSchema("public");
        policy.setTable(TABLE);
        policy.setPartitionColumn("created_at");
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS public." + TABLE + " CASCADE");
    }

    @Test
    void createsFuturePartitionsAndRerunIsIdempotent() {
        LocalDate today = LocalDate.of(2026, 9, 23);

        var first = service.maintain(policy, today);
        var second = service.maintain(policy, today);

        assertThat(first.created()).isEqualTo(3);
        assertThat(first.existing()).isZero();
        assertThat(first.failed()).isZero();

        assertThat(second.created()).isZero();
        assertThat(second.existing()).isEqualTo(3);
        assertThat(second.failed()).isZero();

        assertThat(partitionNames())
                .containsExactly(TABLE + "_20260923", TABLE + "_20260924", TABLE + "_20260925");
    }

    @Test
    void dropsOnlyPartitionsWhoseUpperBoundIsAtOrBeforeFourYearCutoff() {
        LocalDate today = LocalDate.of(2026, 9, 23);
        createPartition(LocalDate.of(2022, 9, 22));
        createPartition(LocalDate.of(2022, 9, 23));

        var result = service.maintain(policy, today);

        assertThat(result.dropped()).isEqualTo(1);
        assertThat(partitionNames())
                .doesNotContain(TABLE + "_20220922")
                .contains(TABLE + "_20220923");
    }

    @Test
    void fourYearCutoffUsesCalendarYearsAcrossLeapDay() {
        LocalDate today = LocalDate.of(2028, 2, 29);
        createPartition(LocalDate.of(2024, 2, 28));
        createPartition(LocalDate.of(2024, 2, 29));

        var result = service.maintain(policy, today);

        assertThat(result.dropped()).isEqualTo(1);
        assertThat(partitionNames())
                .doesNotContain(TABLE + "_20240228")
                .contains(TABLE + "_20240229");
    }

    @Test
    void skipsWhenAnotherMaintainerOwnsAdvisoryLock() throws Exception {
        String lockName = "partition:public." + TABLE;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement lock =
                        connection.prepareStatement("SELECT pg_advisory_lock(hashtext(?))");
                PreparedStatement unlock =
                        connection.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
            lock.setString(1, lockName);
            lock.execute();

            try {
                var result = service.maintain(policy, LocalDate.of(2026, 9, 23));

                assertThat(result.lockSkipped()).isEqualTo(1);
                assertThat(result.created()).isZero();
                assertThat(partitionNames()).isEmpty();
            } finally {
                unlock.setString(1, lockName);
                unlock.execute();
            }
        }
    }

    private void createPartition(LocalDate date) {
        jdbc.execute(
                "CREATE TABLE public."
                        + TABLE
                        + "_"
                        + date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                        + " PARTITION OF public."
                        + TABLE
                        + " FOR VALUES FROM ('"
                        + date
                        + "') TO ('"
                        + date.plusDays(1)
                        + "')");
    }

    private java.util.List<String> partitionNames() {
        return jdbc.queryForList(
                """
                SELECT child.relname
                FROM pg_inherits inheritance
                JOIN pg_class child ON child.oid = inheritance.inhrelid
                JOIN pg_class parent ON parent.oid = inheritance.inhparent
                JOIN pg_namespace ns ON ns.oid = parent.relnamespace
                WHERE ns.nspname = 'public'
                  AND parent.relname = ?
                ORDER BY child.relname
                """,
                String.class,
                TABLE);
    }
}
