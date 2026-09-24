package io.collectra.api.shared.partition;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "collectra.partition-maintenance")
public class PartitionMaintenanceProperties {
    private boolean enabled = false;
    private boolean dryRun = true;
    private String cron = "0 15 2 * * *";
    private int maxPartitionsPerRun = 400;
    private Duration lockTimeout = Duration.ofSeconds(5);
    private Duration statementTimeout = Duration.ofSeconds(30);
    private List<TablePolicy> tables = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getMaxPartitionsPerRun() {
        return maxPartitionsPerRun;
    }

    public void setMaxPartitionsPerRun(int maxPartitionsPerRun) {
        if (maxPartitionsPerRun < 1 || maxPartitionsPerRun > 5000) {
            throw new IllegalArgumentException(
                    "max-partitions-per-run must be between 1 and 5000");
        }
        this.maxPartitionsPerRun = maxPartitionsPerRun;
    }

    public Duration getLockTimeout() {
        return lockTimeout;
    }

    public void setLockTimeout(Duration lockTimeout) {
        this.lockTimeout = requirePositive(lockTimeout, "lock-timeout");
    }

    public Duration getStatementTimeout() {
        return statementTimeout;
    }

    public void setStatementTimeout(Duration statementTimeout) {
        this.statementTimeout = requirePositive(statementTimeout, "statement-timeout");
    }

    public List<TablePolicy> getTables() {
        return tables;
    }

    public void setTables(List<TablePolicy> tables) {
        this.tables = tables == null ? new ArrayList<>() : new ArrayList<>(tables);
    }

    private Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public enum Granularity {
        DAY,
        MONTH
    }

    public enum MaintenanceMode {
        CREATE_ONLY,
        CREATE_AND_DROP
    }

    public enum RetentionUnit {
        DAYS,
        MONTHS,
        YEARS
    }

    public static class TablePolicy {
        private boolean enabled = true;
        private String schema = "public";
        private String table;
        private String partitionColumn = "created_at";
        private Granularity granularity = Granularity.DAY;
        private MaintenanceMode mode = MaintenanceMode.CREATE_ONLY;
        private int createAhead = 14;
        private int retention = 4;
        private RetentionUnit retentionUnit = RetentionUnit.YEARS;
        private Boolean dryRun;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getPartitionColumn() {
            return partitionColumn;
        }

        public void setPartitionColumn(String partitionColumn) {
            this.partitionColumn = partitionColumn;
        }

        public Granularity getGranularity() {
            return granularity;
        }

        public void setGranularity(Granularity granularity) {
            this.granularity = granularity == null ? Granularity.DAY : granularity;
        }

        public MaintenanceMode getMode() {
            return mode;
        }

        public void setMode(MaintenanceMode mode) {
            this.mode = mode == null ? MaintenanceMode.CREATE_ONLY : mode;
        }

        public int getCreateAhead() {
            return createAhead;
        }

        public void setCreateAhead(int createAhead) {
            if (createAhead < 0 || createAhead > 120) {
                throw new IllegalArgumentException("create-ahead must be between 0 and 120");
            }
            this.createAhead = createAhead;
        }

        public int getRetention() {
            return retention;
        }

        public void setRetention(int retention) {
            if (retention < 1 || retention > 10000) {
                throw new IllegalArgumentException("retention must be between 1 and 10000");
            }
            this.retention = retention;
        }

        public RetentionUnit getRetentionUnit() {
            return retentionUnit;
        }

        public void setRetentionUnit(RetentionUnit retentionUnit) {
            this.retentionUnit = retentionUnit == null ? RetentionUnit.YEARS : retentionUnit;
        }

        public Boolean getDryRun() {
            return dryRun;
        }

        public void setDryRun(Boolean dryRun) {
            this.dryRun = dryRun;
        }
    }
}
