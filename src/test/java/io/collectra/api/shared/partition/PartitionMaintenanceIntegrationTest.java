package io.collectra.api.shared.partition;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
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
    @Autowired MeterRegistry meterRegistry;

    private PartitionMaintenanceProperties.TablePolicy policy;

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TABLE IF EXISTS public." + TABLE + " CASCADE");
        jdbc.execute(
                "CREATE TABLE public."
                        + TABLE
                        + " (id BIGINT, created_at TIMESTAMPTZ NOT NULL)"
                        + " PARTITION BY RANGE (created_at)");

        properties.setDryRun(false);
        properties.setMaxPartitionsPerRun(400);

        policy = new PartitionMaintenanceProperties.TablePolicy();
        policy.setSchema("public");
        policy.setTable(TABLE);
        policy.setPartitionColumn("created_at");
        policy.setGranularity(PartitionMaintenanceProperties.Granularity.DAY);
        policy.setMode(PartitionMaintenanceProperties.MaintenanceMode.CREATE_ONLY);
        policy.setCreateAhead(2);
        policy.setRetention(4);
        policy.setRetentionUnit(PartitionMaintenanceProperties.RetentionUnit.YEARS);
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS public." + TABLE + " CASCADE");
    }

    @Test
    void createsDailyPartitionsAheadAndRerunIsIdempotent() {
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
    void createsMonthlyPartitionsFromFirstDayOfCurrentMonth() {
        policy.setGranularity(PartitionMaintenanceProperties.Granularity.MONTH);
        policy.setCreateAhead(2);

        var result = service.maintain(policy, LocalDate.of(2026, 9, 23));

        assertThat(result.created()).isEqualTo(3);
        assertThat(partitionNames())
                .containsExactly(TABLE + "_202609", TABLE + "_202610", TABLE + "_202611");
        assertThat(partitionBounds(TABLE + "_202609"))
                .contains("2026-09-01")
                .contains("2026-10-01");
    }

    @Test
    void createOnlyNeverDropsExpiredPartitions() {
        createDailyPartition(LocalDate.of(2020, 1, 1));

        var result = service.maintain(policy, LocalDate.of(2026, 9, 23));

        assertThat(result.dropped()).isZero();
        assertThat(partitionNames()).contains(TABLE + "_20200101");
    }

    @Test
    void createAndDropRemovesOnlyPartitionsAtOrBeforeRetentionCutoff() {
        policy.setMode(PartitionMaintenanceProperties.MaintenanceMode.CREATE_AND_DROP);
        policy.setRetention(4);
        policy.setRetentionUnit(PartitionMaintenanceProperties.RetentionUnit.YEARS);

        createDailyPartition(LocalDate.of(2022, 9, 21));
        createDailyPartition(LocalDate.of(2022, 9, 22));
        createDailyPartition(LocalDate.of(2022, 9, 23));

        var result = service.maintain(policy, LocalDate.of(2026, 9, 23));

        assertThat(result.dropped()).isEqualTo(2);
        assertThat(partitionNames())
                .doesNotContain(TABLE + "_20220921", TABLE + "_20220922")
                .contains(TABLE + "_20220923");
    }

    @Test
    void monthlyRetentionUsesWholePartitionBoundary() {
        policy.setGranularity(PartitionMaintenanceProperties.Granularity.MONTH);
        policy.setMode(PartitionMaintenanceProperties.MaintenanceMode.CREATE_AND_DROP);
        policy.setRetention(12);
        policy.setRetentionUnit(PartitionMaintenanceProperties.RetentionUnit.MONTHS);

        createMonthlyPartition(LocalDate.of(2025, 8, 1));
        createMonthlyPartition(LocalDate.of(2025, 9, 1));

        var result = service.maintain(policy, LocalDate.of(2026, 9, 23));

        assertThat(result.dropped()).isEqualTo(1);
        assertThat(partitionNames()).doesNotContain(TABLE + "_202508").contains(TABLE + "_202509");
    }

    @Test
    void globalDryRunPlansChangesWithoutExecutingDdl() {
        properties.setDryRun(true);
        policy.setMode(PartitionMaintenanceProperties.MaintenanceMode.CREATE_AND_DROP);
        policy.setCreateAhead(1);
        policy.setRetention(30);
        policy.setRetentionUnit(PartitionMaintenanceProperties.RetentionUnit.DAYS);
        createDailyPartition(LocalDate.of(2026, 1, 1));

        var result = service.maintain(policy, LocalDate.of(2026, 9, 23));

        assertThat(result.dryRun()).isTrue();
        assertThat(result.created()).isZero();
        assertThat(result.dropped()).isZero();
        assertThat(result.plannedCreates()).isEqualTo(2);
        assertThat(result.plannedDrops()).isEqualTo(1);
        assertThat(partitionNames()).containsExactly(TABLE + "_20260101");
    }

    @Test
    void tableDryRunOverridesGlobalSetting() {
        properties.setDryRun(false);
        policy.setDryRun(true);
        policy.setCreateAhead(0);

        var result = service.maintain(policy, LocalDate.of(2026, 9, 23));

        assertThat(result.dryRun()).isTrue();
        assertThat(result.plannedCreates()).isEqualTo(1);
        assertThat(partitionNames()).isEmpty();
    }

    @Test
    void rejectsRunThatExceedsGlobalPartitionSafetyLimit() {
        properties.setMaxPartitionsPerRun(2);
        policy.setCreateAhead(2);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.maintain(policy, LocalDate.of(2026, 9, 23)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many partitions requested");
    }

    @Test
    void skipsMalformedManagedNameAndBoundsInsteadOfDroppingIt() {
        policy.setMode(PartitionMaintenanceProperties.MaintenanceMode.CREATE_AND_DROP);
        policy.setRetention(30);
        policy.setRetentionUnit(PartitionMaintenanceProperties.RetentionUnit.DAYS);

        jdbc.execute(
                "CREATE TABLE public."
                        + TABLE
                        + "_20200101 PARTITION OF public."
                        + TABLE
                        + " FOR VALUES FROM ('2020-01-02') TO ('2020-01-03')");

        var result = service.maintain(policy, LocalDate.of(2026, 9, 23));

        assertThat(result.dropped()).isZero();
        assertThat(partitionNames()).contains(TABLE + "_20200101");
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

    @Test
    void configuredRunContinuesAfterOneTableFails() {
        var broken = new PartitionMaintenanceProperties.TablePolicy();
        broken.setSchema("public");
        broken.setTable("missing_partition_parent");
        broken.setPartitionColumn("created_at");
        broken.setCreateAhead(0);
        broken.setDryRun(false);

        policy.setCreateAhead(0);
        properties.setTables(List.of(broken, policy));

        var result = service.maintainConfiguredTables();

        assertThat(result.tables()).hasSize(2);
        assertThat(result.tables().get(0).failed()).isEqualTo(1);
        assertThat(result.tables().get(0).errors()).isNotEmpty();
        assertThat(result.tables().get(1).failed()).isZero();
        assertThat(result.tables().get(1).created()).isEqualTo(1);
    }

    @Test
    void recordsOperationMetrics() {
        policy.setCreateAhead(0);

        double before =
                counterValue(
                        "collectra.partition.maintenance.operations",
                        "table",
                        "public." + TABLE,
                        "action",
                        "created");

        service.maintain(policy, LocalDate.of(2026, 9, 23));

        assertThat(
                        counterValue(
                                "collectra.partition.maintenance.operations",
                                "table",
                                "public." + TABLE,
                                "action",
                                "created"))
                .isEqualTo(before + 1.0);
    }

    private void createDailyPartition(LocalDate date) {
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

    private void createMonthlyPartition(LocalDate month) {
        LocalDate start = month.withDayOfMonth(1);
        jdbc.execute(
                "CREATE TABLE public."
                        + TABLE
                        + "_"
                        + start.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"))
                        + " PARTITION OF public."
                        + TABLE
                        + " FOR VALUES FROM ('"
                        + start
                        + "') TO ('"
                        + start.plusMonths(1)
                        + "')");
    }

    private List<String> partitionNames() {
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

    private String partitionBounds(String partitionName) {
        return jdbc.queryForObject(
                """
                SELECT pg_get_expr(child.relpartbound, child.oid)
                FROM pg_class child
                JOIN pg_namespace ns ON ns.oid = child.relnamespace
                WHERE ns.nspname = 'public'
                  AND child.relname = ?
                """,
                String.class,
                partitionName);
    }

    private double counterValue(String name, String... tags) {
        var search = meterRegistry.find(name).tags(tags).counter();
        return search == null ? 0.0 : search.count();
    }
}
