package io.collectra.api.reporting.application;

import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "collectra.reporting.projections.enabled", havingValue = "true")
public class CommunicationProjectionScheduler {
    private static final Logger log =
            LoggerFactory.getLogger(CommunicationProjectionScheduler.class);

    private final TenantRepository tenants;
    private final CommunicationProjectionRebuildService rebuilds;
    private final CommunicationProjectionProperties properties;

    public CommunicationProjectionScheduler(
            TenantRepository tenants,
            CommunicationProjectionRebuildService rebuilds,
            CommunicationProjectionProperties properties) {
        this.tenants = tenants;
        this.rebuilds = rebuilds;
        this.properties = properties;
    }

    @Scheduled(cron = "${collectra.reporting.projections.cron:0 20 1 * * *}", zone = "UTC")
    public void rebuildRecentClosedDays() {
        LocalDate today = rebuilds.currentBusinessDate();
        int days = properties.getReconciliationDays();

        int page = 0;
        while (true) {
            var tenantPage = tenants.findAll(PageRequest.of(page, properties.getTenantBatchSize()));
            tenantPage.stream()
                    .filter(tenant -> tenant.active())
                    .forEach(
                            tenant -> {
                                for (int offset = 1; offset <= days; offset++) {
                                    LocalDate date = today.minusDays(offset);
                                    try {
                                        var result =
                                                rebuilds.rebuildTenantDay(tenant.getId(), date);
                                        log.info(
                                                "Communication projection rebuilt. tenantId={}, businessDate={}, campaignRows={}, failureRows={}, sourceWatermark={}",
                                                result.tenantId(),
                                                result.businessDate(),
                                                result.campaignRows(),
                                                result.failureRows(),
                                                result.sourceWatermark());
                                    } catch (RuntimeException ex) {
                                        log.error(
                                                "Communication projection rebuild failed. tenantId={}, businessDate={}",
                                                tenant.getId(),
                                                date,
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
