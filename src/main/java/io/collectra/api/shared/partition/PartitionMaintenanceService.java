package io.collectra.api.shared.partition;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PartitionMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceService.class);
    private static final DateTimeFormatter DAY_SUFFIX = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH_SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    private final JdbcTemplate jdbc;
    private final PartitionMaintenanceProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final ZoneId businessZone;

    public PartitionMaintenanceService(
            JdbcTemplate jdbc,
            PartitionMaintenanceProperties properties,
            MeterRegistry meterRegistry,
            Clock clock,
            ZoneId businessZone) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    public RunResult maintainConfiguredTables() {
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        List<TableResult> results = new ArrayList<>();

        for (PartitionMaintenanceProperties.TablePolicy policy : properties.getTables()) {
            if (!policy.isEnabled()) {
                continue;
            }
            results.add(maintain(policy, today));
        }
        return new RunResult(today, List.copyOf(results));
    }

    TableResult maintain(PartitionMaintenanceProperties.TablePolicy policy, LocalDate today) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(today, "today");
        validateIdentifier(policy.getSchema(), "schema");
        validateIdentifier(policy.getTable(), "table");
        validateIdentifier(policy.getPartitionColumn(), "partitionColumn");
        validatePolicy(policy);

        TableResult result =
                jdbc.execute(
                        (ConnectionCallback<TableResult>)
                                connection -> maintainLocked(connection, policy, today));
        recordMetrics(result);
        return result;
    }

    private TableResult maintainLocked(
            Connection connection,
            PartitionMaintenanceProperties.TablePolicy policy,
            LocalDate today)
            throws SQLException {
        String lockName = "partition:" + policy.getSchema() + "." + policy.getTable();
        boolean dryRun = effectiveDryRun(policy);

        if (!tryLock(connection, lockName)) {
            log.info("Partition maintenance lock busy. table={}", lockName);
            return TableResult.lockSkipped(policy, dryRun);
        }

        SessionSettings sessionSettings = readSessionSettings(connection);
        long startedAt = System.nanoTime();
        int created = 0;
        int existing = 0;
        int dropped = 0;
        int plannedCreates = 0;
        int plannedDrops = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            configureSession(connection);
            verifyRangePartitionedParent(connection, policy);

            LocalDate currentStart = floor(today, policy.getGranularity());
            int requestedPartitions = policy.getCreateAhead() + 1;
            if (requestedPartitions > properties.getMaxPartitionsPerRun()) {
                throw new IllegalArgumentException(
                        "Too many partitions requested for "
                                + policy.getTable()
                                + ": "
                                + requestedPartitions);
            }

            LocalDate start = currentStart;
            for (int i = 0; i < requestedPartitions; i++) {
                String partition = partitionName(policy, start);
                try {
                    if (partitionExists(
                            connection, policy.getSchema(), policy.getTable(), partition)) {
                        existing++;
                    } else if (dryRun) {
                        plannedCreates++;
                        log.info(
                                "Partition create planned (dry-run). table={}.{}, from={}, to={}",
                                policy.getSchema(),
                                partition,
                                start,
                                next(start, policy.getGranularity()));
                    } else {
                        createPartition(connection, policy, start, partition);
                        created++;
                    }
                } catch (SQLException ex) {
                    failed++;
                    errors.add(partition + ": " + ex.getMessage());
                    log.error(
                            "Failed to create partition {}.{}", policy.getSchema(), partition, ex);
                }
                start = next(start, policy.getGranularity());
            }

            if (policy.getMode()
                    == PartitionMaintenanceProperties.MaintenanceMode.CREATE_AND_DROP) {
                LocalDate cutoff = retentionCutoff(today, policy);
                for (PartitionInfo partition :
                        findManagedPartitions(connection, policy.getSchema(), policy.getTable())) {
                    LocalDate partitionStart =
                            partitionDate(
                                    policy.getTable(),
                                    partition.name(),
                                    policy.getGranularity());
                    if (partitionStart == null) {
                        continue;
                    }
                    LocalDate partitionEnd = next(partitionStart, policy.getGranularity());
                    if (partitionEnd.isAfter(cutoff)) {
                        continue;
                    }
                    if (!boundsMatch(
                            partition.boundExpression(), partitionStart, partitionEnd)) {
                        log.warn(
                                "Partition bounds do not match managed name. table={}.{}, bound={}",
                                policy.getSchema(),
                                partition.name(),
                                partition.boundExpression());
                        continue;
                    }

                    if (dryRun) {
                        plannedDrops++;
                        log.info(
                                "Partition drop planned (dry-run). table={}.{}, cutoff={}",
                                policy.getSchema(),
                                partition.name(),
                                cutoff);
                        continue;
                    }

                    try {
                        dropPartition(connection, policy.getSchema(), partition.name());
                        dropped++;
                    } catch (SQLException ex) {
                        failed++;
                        errors.add(partition.name() + ": " + ex.getMessage());
                        log.error(
                                "Failed to drop expired partition {}.{}",
                                policy.getSchema(),
                                partition.name(),
                                ex);
                    }
                }
            }

            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            TableResult result =
                    new TableResult(
                            policy.getSchema(),
                            policy.getTable(),
                            policy.getGranularity(),
                            policy.getMode(),
                            dryRun,
                            created,
                            existing,
                            dropped,
                            plannedCreates,
                            plannedDrops,
                            failed,
                            0,
                            durationMs,
                            List.copyOf(errors));
            log.info("Partition maintenance completed. result={}", result);
            return result;
        } finally {
            restoreSession(connection, sessionSettings);
            unlock(connection, lockName);
        }
    }

    private void verifyRangePartitionedParent(
            Connection connection, PartitionMaintenanceProperties.TablePolicy policy)
            throws SQLException {
        String sql =
                """
                SELECT pg_get_partkeydef(parent.oid)
                FROM pg_class parent
                JOIN pg_namespace ns ON ns.oid = parent.relnamespace
                JOIN pg_partitioned_table partitioned ON partitioned.partrelid = parent.oid
                WHERE ns.nspname = ?
                  AND parent.relname = ?
                  AND partitioned.partstrat = 'r'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, policy.getSchema());
            statement.setString(2, policy.getTable());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "Table is not RANGE partitioned: "
                                    + policy.getSchema()
                                    + "."
                                    + policy.getTable());
                }
                String key = result.getString(1);
                if (key == null || !key.contains(policy.getPartitionColumn())) {
                    throw new IllegalStateException(
                            "Partition key does not contain expected column "
                                    + policy.getPartitionColumn()
                                    + ": "
                                    + key);
                }
            }
        }
    }

    private void createPartition(
            Connection connection,
            PartitionMaintenanceProperties.TablePolicy policy,
            LocalDate start,
            String partition)
            throws SQLException {
        LocalDate end = next(start, policy.getGranularity());
        String sql =
                "CREATE TABLE "
                        + qualified(policy.getSchema(), partition)
                        + " PARTITION OF "
                        + qualified(policy.getSchema(), policy.getTable())
                        + " FOR VALUES FROM ('"
                        + start
                        + "') TO ('"
                        + end
                        + "')";

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void dropPartition(Connection connection, String schema, String partition)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + qualified(schema, partition));
        }
    }

    private List<PartitionInfo> findManagedPartitions(
            Connection connection, String schema, String parentTable) throws SQLException {
        String sql =
                """
                SELECT child.relname, pg_get_expr(child.relpartbound, child.oid) AS bound_expression
                FROM pg_inherits inheritance
                JOIN pg_class child ON child.oid = inheritance.inhrelid
                JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
                JOIN pg_class parent ON parent.oid = inheritance.inhparent
                JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
                WHERE parent_ns.nspname = ?
                  AND parent.relname = ?
                  AND child_ns.nspname = ?
                  AND child.relispartition = true
                ORDER BY child.relname
                """;

        List<PartitionInfo> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, parentTable);
            statement.setString(3, schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new PartitionInfo(rs.getString(1), rs.getString(2)));
                }
            }
        }
        return result;
    }

    private boolean partitionExists(
            Connection connection, String schema, String parentTable, String partition)
            throws SQLException {
        String sql =
                """
                SELECT 1
                FROM pg_inherits inheritance
                JOIN pg_class child ON child.oid = inheritance.inhrelid
                JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
                JOIN pg_class parent ON parent.oid = inheritance.inhparent
                JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
                WHERE parent_ns.nspname = ?
                  AND parent.relname = ?
                  AND child_ns.nspname = ?
                  AND child.relname = ?
                  AND child.relispartition = true
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, parentTable);
            statement.setString(3, schema);
            statement.setString(4, partition);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean tryLock(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))")) {
            statement.setString(1, lockName);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void unlock(Connection connection, String lockName) {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
            statement.setString(1, lockName);
            statement.execute();
        } catch (SQLException ex) {
            log.warn("Failed to release partition advisory lock. lockName={}", lockName, ex);
        }
    }

    private SessionSettings readSessionSettings(Connection connection) throws SQLException {
        return new SessionSettings(
                currentSetting(connection, "lock_timeout"),
                currentSetting(connection, "statement_timeout"),
                currentSetting(connection, "TimeZone"));
    }

    private String currentSetting(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT current_setting(?)")) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private void configureSession(Connection connection) throws SQLException {
        setConfig(connection, "lock_timeout", properties.getLockTimeout().toMillis() + "ms");
        setConfig(
                connection,
                "statement_timeout",
                properties.getStatementTimeout().toMillis() + "ms");
        setConfig(connection, "TimeZone", businessZone.getId());
    }

    private void restoreSession(Connection connection, SessionSettings settings) {
        try {
            setConfig(connection, "lock_timeout", settings.lockTimeout());
            setConfig(connection, "statement_timeout", settings.statementTimeout());
            setConfig(connection, "TimeZone", settings.timeZone());
        } catch (SQLException ex) {
            log.warn("Failed to restore PostgreSQL session settings", ex);
        }
    }

    private void setConfig(Connection connection, String name, String value) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT set_config(?, ?, false)")) {
            statement.setString(1, name);
            statement.setString(2, value);
            statement.execute();
        }
    }

    private boolean effectiveDryRun(PartitionMaintenanceProperties.TablePolicy policy) {
        return policy.getDryRun() != null ? policy.getDryRun() : properties.isDryRun();
    }

    private static void validatePolicy(PartitionMaintenanceProperties.TablePolicy policy) {
        Objects.requireNonNull(policy.getGranularity(), "granularity");
        Objects.requireNonNull(policy.getMode(), "mode");
        Objects.requireNonNull(policy.getRetentionUnit(), "retentionUnit");
    }

    private static LocalDate floor(
            LocalDate date, PartitionMaintenanceProperties.Granularity granularity) {
        return granularity == PartitionMaintenanceProperties.Granularity.MONTH
                ? date.withDayOfMonth(1)
                : date;
    }

    private static LocalDate next(
            LocalDate start, PartitionMaintenanceProperties.Granularity granularity) {
        return granularity == PartitionMaintenanceProperties.Granularity.MONTH
                ? start.plusMonths(1)
                : start.plusDays(1);
    }

    private static LocalDate retentionCutoff(
            LocalDate today, PartitionMaintenanceProperties.TablePolicy policy) {
        LocalDate cutoff =
                switch (policy.getRetentionUnit()) {
                    case DAYS -> today.minusDays(policy.getRetention());
                    case MONTHS -> today.minusMonths(policy.getRetention());
                    case YEARS -> today.minusYears(policy.getRetention());
                };
        return floor(cutoff, policy.getGranularity());
    }

    private static String partitionName(
            PartitionMaintenanceProperties.TablePolicy policy, LocalDate start) {
        DateTimeFormatter formatter =
                policy.getGranularity() == PartitionMaintenanceProperties.Granularity.MONTH
                        ? MONTH_SUFFIX
                        : DAY_SUFFIX;
        return policy.getTable() + "_" + start.format(formatter);
    }

    private static LocalDate partitionDate(
            String parentTable,
            String partitionName,
            PartitionMaintenanceProperties.Granularity granularity) {
        String prefix = parentTable + "_";
        if (!partitionName.startsWith(prefix)) {
            return null;
        }

        String suffix = partitionName.substring(prefix.length());
        try {
            if (granularity == PartitionMaintenanceProperties.Granularity.MONTH) {
                if (suffix.length() != 6 || !suffix.chars().allMatch(Character::isDigit)) {
                    return null;
                }
                return LocalDate.parse(suffix + "01", DAY_SUFFIX);
            }

            if (suffix.length() != 8 || !suffix.chars().allMatch(Character::isDigit)) {
                return null;
            }
            return LocalDate.parse(suffix, DAY_SUFFIX);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean boundsMatch(
            String expression, LocalDate start, LocalDate end) {
        return expression != null
                && expression.contains(start.toString())
                && expression.contains(end.toString());
    }

    private void recordMetrics(TableResult result) {
        String table = result.schema() + "." + result.table();
        increment("created", table, result.created());
        increment("existing", table, result.existing());
        increment("dropped", table, result.dropped());
        increment("planned_create", table, result.plannedCreates());
        increment("planned_drop", table, result.plannedDrops());
        increment("failed", table, result.failed());
        increment("lock_skipped", table, result.lockSkipped());
        meterRegistry
                .timer(
                        "collectra.partition.maintenance.duration",
                        "table",
                        table,
                        "granularity",
                        result.granularity().name())
                .record(java.time.Duration.ofMillis(result.durationMs()));
    }

    private void increment(String action, String table, int amount) {
        if (amount <= 0) {
            return;
        }
        meterRegistry
                .counter("collectra.partition.maintenance.operations", "table", table, "action", action)
                .increment(amount);
    }

    private static String qualified(String schema, String table) {
        return "\""
                + schema.replace("\"", "\"\"")
                + "\".\""
                + table.replace("\"", "\"\"")
                + "\"";
    }

    private static void validateIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
    }

    private record PartitionInfo(String name, String boundExpression) {}

    private record SessionSettings(String lockTimeout, String statementTimeout, String timeZone) {}

    public record RunResult(LocalDate businessDate, List<TableResult> tables) {}

    public record TableResult(
            String schema,
            String table,
            PartitionMaintenanceProperties.Granularity granularity,
            PartitionMaintenanceProperties.MaintenanceMode mode,
            boolean dryRun,
            int created,
            int existing,
            int dropped,
            int plannedCreates,
            int plannedDrops,
            int failed,
            int lockSkipped,
            long durationMs,
            List<String> errors) {
        static TableResult lockSkipped(
                PartitionMaintenanceProperties.TablePolicy policy, boolean dryRun) {
            return new TableResult(
                    policy.getSchema(),
                    policy.getTable(),
                    policy.getGranularity(),
                    policy.getMode(),
                    dryRun,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    List.of());
        }
    }
}
