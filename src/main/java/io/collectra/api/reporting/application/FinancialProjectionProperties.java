package io.collectra.api.reporting.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "collectra.reporting.financial-projections")
public class FinancialProjectionProperties {
    private boolean enabled = false;
    private int reconciliationDays = 7;
    private int tenantBatchSize = 100;
    private Duration buildingTimeout = Duration.ofMinutes(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getReconciliationDays() {
        return reconciliationDays;
    }

    public void setReconciliationDays(int value) {
        if (value < 1 || value > 31)
            throw new IllegalArgumentException("reconciliation-days must be between 1 and 31");
        this.reconciliationDays = value;
    }

    public int getTenantBatchSize() {
        return tenantBatchSize;
    }

    public void setTenantBatchSize(int value) {
        if (value < 1 || value > 1000)
            throw new IllegalArgumentException("tenant-batch-size must be between 1 and 1000");
        this.tenantBatchSize = value;
    }

    public Duration getBuildingTimeout() {
        return buildingTimeout;
    }

    public void setBuildingTimeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative())
            throw new IllegalArgumentException("building-timeout must be positive");
        this.buildingTimeout = value;
    }
}
