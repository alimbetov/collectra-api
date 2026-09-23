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
    private String cron = "0 15 2 * * *";
    private int daysAhead = 14;
    private int retentionYears = 4;
    private Duration lockTimeout = Duration.ofSeconds(5);
    private Duration statementTimeout = Duration.ofSeconds(30);
    private List<TablePolicy> tables = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getDaysAhead() {
        return daysAhead;
    }

    public void setDaysAhead(int daysAhead) {
        if (daysAhead < 1 || daysAhead > 31) {
            throw new IllegalArgumentException("days-ahead must be between 1 and 31");
        }
        this.daysAhead = daysAhead;
    }

    public int getRetentionYears() {
        return retentionYears;
    }

    public void setRetentionYears(int retentionYears) {
        if (retentionYears < 1 || retentionYears > 20) {
            throw new IllegalArgumentException("retention-years must be between 1 and 20");
        }
        this.retentionYears = retentionYears;
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

    public static class TablePolicy {
        private boolean enabled = true;
        private String schema = "public";
        private String table;
        private String partitionColumn = "created_at";

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
    }
}
