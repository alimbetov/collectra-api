package io.collectra.api.reporting.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "collectra.reporting.projections")
public class CommunicationProjectionProperties {
    private boolean enabled = false;
    private String cron = "0 20 1 * * *";
    private int reconciliationDays = 7;
    private int tenantBatchSize = 100;

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

    public int getReconciliationDays() {
        return reconciliationDays;
    }

    public void setReconciliationDays(int reconciliationDays) {
        if (reconciliationDays < 1 || reconciliationDays > 31) {
            throw new IllegalArgumentException("reconciliation-days must be between 1 and 31");
        }
        this.reconciliationDays = reconciliationDays;
    }

    public int getTenantBatchSize() {
        return tenantBatchSize;
    }

    public void setTenantBatchSize(int tenantBatchSize) {
        if (tenantBatchSize < 1 || tenantBatchSize > 1000) {
            throw new IllegalArgumentException("tenant-batch-size must be between 1 and 1000");
        }
        this.tenantBatchSize = tenantBatchSize;
    }


}
