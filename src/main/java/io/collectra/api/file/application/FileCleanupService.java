package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import io.collectra.api.file.infrastructure.persistence.StoredFileRepository;
import io.collectra.api.file.infrastructure.storage.FileStorageProperties;
import io.collectra.api.file.infrastructure.storage.ObjectStorage;
import io.collectra.api.file.infrastructure.storage.StorageLocation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FileCleanupService {
    private static final Logger log = LoggerFactory.getLogger(FileCleanupService.class);

    private final StoredFileRepository files;
    private final ObjectStorage storage;
    private final FileStorageProperties properties;
    private final TransactionTemplate transactions;
    private final Counter processedCounter;
    private final Counter deletedCounter;
    private final Counter failedCounter;
    private final Counter exhaustedCounter;

    public FileCleanupService(
            StoredFileRepository files,
            ObjectStorage storage,
            FileStorageProperties properties,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry) {
        this.files = files;
        this.storage = storage;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
        this.processedCounter = meterRegistry.counter("collectra.file.cleanup.processed");
        this.deletedCounter = meterRegistry.counter("collectra.file.cleanup.deleted");
        this.failedCounter = meterRegistry.counter("collectra.file.cleanup.failed");
        this.exhaustedCounter = meterRegistry.counter("collectra.file.cleanup.exhausted");
    }

    public CleanupResult cleanupExpiredFiles() {
        return cleanupExpiredFiles(Instant.now());
    }

    CleanupResult cleanupExpiredFiles(Instant now) {
        int processed = 0;
        int deleted = 0;
        int failed = 0;
        int exhausted = 0;
        int batches = 0;
        int batchSize = properties.getCleanup().getBatchSize();
        int maxBatches = properties.getCleanup().getMaxBatchesPerRun();

        while (batches < maxBatches) {
            List<CleanupCandidate> candidates = claimBatch(now);
            if (candidates.isEmpty()) break;
            batches++;

            for (CleanupCandidate candidate : candidates) {
                processed++;
                processedCounter.increment();
                try {
                    storage.delete(candidate.location());
                    markDeleted(candidate.fileId(), Instant.now());
                    deleted++;
                    deletedCounter.increment();
                } catch (RuntimeException ex) {
                    FailureResult failure =
                            registerFailure(
                                    candidate.fileId(), candidate.claimedAt(), ex.getMessage());
                    failed++;
                    failedCounter.increment();
                    if (failure.exhausted()) {
                        exhausted++;
                        exhaustedCounter.increment();
                    }
                    log.warn(
                            "File cleanup failed fileId={} tenantId={} category={} deleteAttempt={} maxDeleteAttempts={} attemptResult={}",
                            candidate.fileId(),
                            candidate.tenantId(),
                            candidate.category(),
                            failure.deleteAttempts(),
                            properties.getCleanup().getMaxDeleteAttempts(),
                            failure.exhausted() ? "exhausted" : "retryable",
                            ex);
                }
            }

            if (candidates.size() < batchSize) break;
        }

        return new CleanupResult(processed, deleted, failed, exhausted, batches);
    }

    private List<CleanupCandidate> claimBatch(Instant now) {
        List<CleanupCandidate> result =
                transactions.execute(
                        status -> {
                            var cleanup = properties.getCleanup();
                            Instant retryBefore = now.minus(cleanup.getRetryDelay());
                            List<UUID> ids =
                                    files.lockCleanupCandidateIds(
                                            now,
                                            retryBefore,
                                            cleanup.getMaxDeleteAttempts(),
                                            cleanup.getBatchSize());
                            if (ids.isEmpty()) return List.of();

                            List<StoredFile> entities = files.findAllById(ids);
                            List<CleanupCandidate> claimed = new ArrayList<>(entities.size());
                            for (StoredFile file : entities) {
                                file.claimDeleteAttempt(now);
                                claimed.add(CleanupCandidate.from(file, now));
                            }
                            files.saveAllAndFlush(entities);
                            return List.copyOf(claimed);
                        });
        return result == null ? List.of() : result;
    }

    private void markDeleted(UUID fileId, Instant deletedAt) {
        transactions.executeWithoutResult(
                status -> {
                    StoredFile current = files.findById(fileId).orElse(null);
                    if (current == null || current.getStatus() == FileStatus.DELETED) return;
                    if (current.getStatus() != FileStatus.DELETE_PENDING) {
                        log.warn(
                                "Skipping cleanup completion for fileId={} because status={}",
                                fileId,
                                current.getStatus());
                        return;
                    }
                    current.markDeleted(deletedAt);
                    files.save(current);
                });
    }

    private FailureResult registerFailure(UUID fileId, Instant attemptedAt, String error) {
        FailureResult result =
                transactions.execute(
                        status -> {
                            StoredFile current = files.findById(fileId).orElse(null);
                            if (current == null || current.getStatus() == FileStatus.DELETED) {
                                return new FailureResult(false, 0);
                            }
                            if (current.getStatus() != FileStatus.DELETE_PENDING) {
                                log.warn(
                                        "Skipping cleanup failure registration for fileId={} because status={}",
                                        fileId,
                                        current.getStatus());
                                return new FailureResult(
                                        current.getStatus() == FileStatus.DELETE_FAILED,
                                        current.getDeleteAttempts());
                            }
                            boolean exhausted =
                                    current.registerDeleteFailure(
                                            attemptedAt,
                                            error,
                                            properties.getCleanup().getMaxDeleteAttempts());
                            files.save(current);
                            return new FailureResult(exhausted, current.getDeleteAttempts());
                        });
        return result == null ? new FailureResult(false, 0) : result;
    }

    private record CleanupCandidate(
            UUID fileId,
            UUID tenantId,
            io.collectra.api.file.domain.FileCategory category,
            StorageLocation location,
            Instant claimedAt) {
        private static CleanupCandidate from(StoredFile file, Instant claimedAt) {
            return new CleanupCandidate(
                    file.getId(),
                    file.getTenantId(),
                    file.getCategory(),
                    new StorageLocation(file.getBucket(), file.getObjectKey()),
                    claimedAt);
        }
    }

    private record FailureResult(boolean exhausted, int deleteAttempts) {}

    public record CleanupResult(
            int processed, int deleted, int failed, int exhausted, int batches) {}
}
