package io.collectra.api.file.infrastructure.persistence;

import io.collectra.api.file.domain.FileStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class FileStatusMetrics {
    public FileStatusMetrics(StoredFileRepository files, MeterRegistry registry) {
        Gauge.builder(
                        "collectra.file.cleanup.pending",
                        files,
                        repository -> repository.countByStatus(FileStatus.DELETE_PENDING))
                .description("Number of files waiting for cleanup retry")
                .register(registry);
        Gauge.builder(
                        "collectra.file.cleanup.delete.failed",
                        files,
                        repository -> repository.countByStatus(FileStatus.DELETE_FAILED))
                .description("Number of files whose cleanup retries are exhausted")
                .register(registry);
    }
}
