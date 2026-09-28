package io.collectra.api.reporting.application;

import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "collectra.reporting.financial-projections.enabled",
        havingValue = "true")
public class FinancialProjectionScheduler {
    private static final Logger log = LoggerFactory.getLogger(FinancialProjectionScheduler.class);

    private final TenantRepository tenants;
    private final FinancialProjectionRebuildService rebuilds;
    private final FinancialProjectionProperties properties;

    public FinancialProjectionScheduler(
            TenantRepository tenants,
            FinancialProjectionRebuildService rebuilds,
            FinancialProjectionProperties properties) {
        this.tenants = tenants;
        this.rebuilds = rebuilds;
        this.properties = properties;
    }

    @Scheduled(
            cron = "${collectra.reporting.financial-projections.cron:0 35 1 * * *}",
            zone = "UTC")
    public void rebuildRecentClosedDays() {
        LocalDate today = rebuilds.currentBusinessDate();
        int page = 0;
        while (true) {
            var tenantPage =
                    tenants.findAll(
                            PageRequest.of(
                                    page,
                                    properties.getTenantBatchSize(),
                                    Sort.by(Sort.Direction.ASC, "id")));
            tenantPage.stream()
                    .filter(tenant -> tenant.active())
                    .forEach(
                            tenant -> {
                                for (int offset = 1;
                                        offset <= properties.getReconciliationDays();
                                        offset++) {
                                    LocalDate day = today.minusDays(offset);
                                    try {
                                        var result = rebuilds.rebuildTenantDay(tenant.getId(), day);
                                        if (!result.lockSkipped()) {
                                            log.info(
                                                    "Financial projection rebuilt. tenantId={}, businessDate={}, metricRows={}, sourceWatermark={}",
                                                    result.tenantId(),
                                                    day,
                                                    result.metricRows(),
                                                    result.sourceWatermark());
                                        }
                                    } catch (RuntimeException ex) {
                                        log.error(
                                                "Financial projection rebuild failed. tenantId={}, businessDate={}",
                                                tenant.getId(),
                                                day,
                                                ex);
                                    }
                                }
                            });
            if (!tenantPage.hasNext()) {
                break;
            }
            page++;
        }
    }
}
