package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import io.collectra.api.file.infrastructure.persistence.StoredFileRepository;
import io.collectra.api.file.infrastructure.storage.FileStorageProperties;
import io.collectra.api.file.infrastructure.storage.ObjectStorage;
import io.collectra.api.file.infrastructure.storage.StorageLocation;
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

    public FileCleanupService(
            StoredFileRepository files,
            ObjectStorage storage,
            FileStorageProperties properties,
            PlatformTransactionManager transactionManager) {
        this.files = files;
        this.storage = storage;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public CleanupResult cleanupExpiredFiles() {
        return cleanupExpiredFiles(Instant.now());
    }

    CleanupResult cleanupExpiredFiles(Instant now) {
        int processed = 0;
        int deleted = 0;
        int failed = 0;
        int batches = 0;
        int batchSize = properties.getCleanup().getBatchSize();
        int maxBatches = properties.getCleanup().getMaxBatchesPerRun();

        while (batches < maxBatches) {
            List<CleanupCandidate> candidates = claimBatch(now);
            if (candidates.isEmpty()) {
                break;
            }
            batches++;

            for (CleanupCandidate candidate : candidates) {
                processed++;
                try {
                    storage.delete(candidate.location());
                    markDeleted(candidate.fileId(), Instant.now());
                    deleted++;
                } catch (RuntimeException ex) {
                    registerFailure(candidate.fileId(), Instant.now(), ex.getMessage());
                    failed++;
                    log.warn(
                            "File cleanup failed fileId={} tenantId={} category={} attemptResult=retryable",
                            candidate.fileId(),
                            candidate.tenantId(),
                            candidate.category(),
                            ex);
                }
            }

            if (candidates.size() < batchSize) {
                break;
            }
        }

        return new CleanupResult(processed, deleted, failed, batches);
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
                            if (ids.isEmpty()) {
                                return List.of();
                            }

                            List<StoredFile> entities = files.findAllById(ids);
                            List<CleanupCandidate> claimed = new ArrayList<>(entities.size());
                            for (StoredFile file : entities) {
                                file.claimDeleteAttempt(now);
                                claimed.add(CleanupCandidate.from(file));
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
                    if (current == null || current.getStatus() == FileStatus.DELETED) {
                        return;
                    }
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

    private void registerFailure(UUID fileId, Instant attemptedAt, String error) {
        transactions.executeWithoutResult(
                status -> {
                    StoredFile current = files.findById(fileId).orElse(null);
                    if (current == null || current.getStatus() == FileStatus.DELETED) {
                        return;
                    }
                    if (current.getStatus() != FileStatus.DELETE_PENDING) {
                        log.warn(
                                "Skipping cleanup failure registration for fileId={} because status={}",
                                fileId,
                                current.getStatus());
                        return;
                    }
                    current.registerDeleteFailure(attemptedAt, error);
                    files.save(current);
                });
    }

    private record CleanupCandidate(
            UUID fileId,
            UUID tenantId,
            io.collectra.api.file.domain.FileCategory category,
            StorageLocation location) {
        private static CleanupCandidate from(StoredFile file) {
            return new CleanupCandidate(
                    file.getId(),
                    file.getTenantId(),
                    file.getCategory(),
                    new StorageLocation(file.getBucket(), file.getObjectKey()));
        }
    }

    public record CleanupResult(int processed, int deleted, int failed, int batches) {}
}
