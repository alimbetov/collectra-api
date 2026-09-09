package io.collectra.api.file.infrastructure.scheduler;

import io.collectra.api.file.application.FileCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "collectra.file.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class FileCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);

    private final FileCleanupService cleanupService;

    public FileCleanupScheduler(FileCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${collectra.file.cleanup.cron:0 0 3 * * *}")
    public void cleanup() {
        FileCleanupService.CleanupResult result = cleanupService.cleanupExpiredFiles();
        log.info(
                "File cleanup completed processed={} deleted={} failed={} batches={}",
                result.processed(),
                result.deleted(),
                result.failed(),
                result.batches());
    }
}
