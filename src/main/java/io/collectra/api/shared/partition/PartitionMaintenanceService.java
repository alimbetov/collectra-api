package io.collectra.api.shared.partition;

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
    private static final DateTimeFormatter PARTITION_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    private final JdbcTemplate jdbc;
    private final PartitionMaintenanceProperties properties;
    private final Clock clock;
    private final ZoneId businessZone;

    public PartitionMaintenanceService(
            JdbcTemplate jdbc,
            PartitionMaintenanceProperties properties,
            Clock clock,
            ZoneId businessZone) {
        this.jdbc = jdbc;
        this.properties = properties;
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

        return jdbc.execute(
                (ConnectionCallback<TableResult>)
                        connection -> maintainLocked(connection, policy, today));
    }

    private TableResult maintainLocked(
            Connection connection,
            PartitionMaintenanceProperties.TablePolicy policy,
            LocalDate today)
            throws SQLException {
        String lockName = "partition:" + policy.getSchema() + "." + policy.getTable();

        if (!tryLock(connection, lockName)) {
            log.info("Partition maintenance lock busy. table={}", lockName);
            return TableResult.lockSkipped(policy.getSchema(), policy.getTable());
        }

        long startedAt = System.nanoTime();
        int created = 0;
        int existing = 0;
        int dropped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            configureTimeouts(connection);
            verifyRangePartitionedParent(connection, policy);

            LocalDate createUntil = today.plusDays(properties.getDaysAhead());
            for (LocalDate date = today; !date.isAfter(createUntil); date = date.plusDays(1)) {
                String partition = partitionName(policy.getTable(), date);
                try {
                    if (partitionExists(
                            connection, policy.getSchema(), policy.getTable(), partition)) {
                        existing++;
                    } else {
                        createPartition(connection, policy, date, partition);
                        created++;
                    }
                } catch (SQLException ex) {
                    failed++;
                    errors.add(partition + ": " + ex.getMessage());
                    log.error(
                            "Failed to create partition {}.{}", policy.getSchema(), partition, ex);
                }
            }

            LocalDate cutoff = today.minusYears(properties.getRetentionYears());
            for (PartitionInfo partition :
                    findManagedPartitions(connection, policy.getSchema(), policy.getTable())) {
                LocalDate date = partitionDate(policy.getTable(), partition.name());
                if (date == null || date.plusDays(1).isAfter(cutoff)) {
                    continue;
                }
                if (!boundsMatchDate(partition.boundExpression(), date)) {
                    log.warn(
                            "Partition bounds do not match managed name. table={}.{}, bound={}",
                            policy.getSchema(),
                            partition.name(),
                            partition.boundExpression());
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

            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            TableResult result =
                    new TableResult(
                            policy.getSchema(),
                            policy.getTable(),
                            created,
                            existing,
                            dropped,
                            failed,
                            0,
                            durationMs,
                            List.copyOf(errors));
            log.info("Partition maintenance completed. result={}", result);
            return result;
        } finally {
            resetTimeouts(connection);
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
            LocalDate date,
            String partition)
            throws SQLException {
        String sql =
                "CREATE TABLE "
                        + qualified(policy.getSchema(), partition)
                        + " PARTITION OF "
                        + qualified(policy.getSchema(), policy.getTable())
                        + " FOR VALUES FROM ('"
                        + date
                        + "') TO ('"
                        + date.plusDays(1)
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

    private void configureTimeouts(Connection connection) throws SQLException {
        setConfig(connection, "lock_timeout", properties.getLockTimeout().toMillis() + "ms");
        setConfig(
                connection,
                "statement_timeout",
                properties.getStatementTimeout().toMillis() + "ms");
    }

    private void resetTimeouts(Connection connection) {
        try {
            setConfig(connection, "lock_timeout", "0");
            setConfig(connection, "statement_timeout", "0");
        } catch (SQLException ex) {
            log.warn("Failed to reset PostgreSQL session timeouts", ex);
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

    private static String partitionName(String parentTable, LocalDate date) {
        return parentTable + "_" + date.format(PARTITION_DATE);
    }

    private static LocalDate partitionDate(String parentTable, String partitionName) {
        String prefix = parentTable + "_";
        if (!partitionName.startsWith(prefix)) {
            return null;
        }
        String suffix = partitionName.substring(prefix.length());
        if (suffix.length() != 8 || !suffix.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return LocalDate.parse(suffix, PARTITION_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean boundsMatchDate(String expression, LocalDate date) {
        return expression != null
                && expression.contains(date.toString())
                && expression.contains(date.plusDays(1).toString());
    }

    private static String qualified(String schema, String table) {
        return "\"" + schema.replace("\"", "\"\"") + "\".\"" + table.replace("\"", "\"\"") + "\"";
    }

    private static void validateIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
    }

    private record PartitionInfo(String name, String boundExpression) {}

    public record RunResult(LocalDate businessDate, List<TableResult> tables) {}

    public record TableResult(
            String schema,
            String table,
            int created,
            int existing,
            int dropped,
            int failed,
            int lockSkipped,
            long durationMs,
            List<String> errors) {
        static TableResult lockSkipped(String schema, String table) {
            return new TableResult(schema, table, 0, 0, 0, 0, 1, 0, List.of());
        }
    }
}
