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

        int failed =
                result.tables().stream()
                        .mapToInt(PartitionMaintenanceService.TableResult::failed)
                        .sum();
        int lockSkipped =
                result.tables().stream()
                        .mapToInt(PartitionMaintenanceService.TableResult::lockSkipped)
                        .sum();
        int planned =
                result.tables().stream()
                        .mapToInt(table -> table.plannedCreates() + table.plannedDrops())
                        .sum();

        if (failed > 0) {
            log.error(
                    "Partition maintenance run completed with failures. businessDate={}, tables={}, failed={}, lockSkipped={}, planned={}",
                    result.businessDate(),
                    result.tables().size(),
                    failed,
                    lockSkipped,
                    planned);
            return;
        }

        log.info(
                "Partition maintenance run finished. businessDate={}, tables={}, failed=0, lockSkipped={}, planned={}",
                result.businessDate(),
                result.tables().size(),
                lockSkipped,
                planned);
    }
}
