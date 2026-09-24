package io.collectra.api.reporting.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "collectra.reporting.projections")
public class CommunicationProjectionProperties {
    private boolean enabled = false;
    private String cron = "0 20 1 * * *";
    private int reconciliationDays = 7;
    private Duration cacheTtl = Duration.ofMinutes(5);

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

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("cache-ttl must be positive");
        }
        this.cacheTtl = cacheTtl;
    }
}
