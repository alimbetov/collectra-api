package io.collectra.api.shared.partition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "collectra.partition-maintenance.enabled", havingValue = "true")
public class PartitionMaintenanceScheduler {
    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceScheduler.class);

    private final PartitionMaintenanceService service;

    public PartitionMaintenanceScheduler(PartitionMaintenanceService service) {
        this.service = service;
    }

    @Scheduled(
            cron = "${collectra.partition-maintenance.cron:0 15 2 * * *}",
            zone = "${collectra.business-zone:Asia/Almaty}")
    public void maintain() {
        PartitionMaintenanceService.RunResult result = service.maintainConfiguredTables();
        log.info(
                "Partition maintenance run finished. businessDate={}, tables={}",
                result.businessDate(),
                result.tables().size());
    }
}
