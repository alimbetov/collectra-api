package io.collectra.api.integration.application;

import io.collectra.api.file.application.FileService;
import io.collectra.api.file.infrastructure.storage.FileStorageException;
import io.collectra.api.importing.application.MappingExecutionService;
import io.collectra.api.integration.domain.*;
import io.collectra.api.integration.infrastructure.*;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IngestionWorker {
    private final IngestionBatchRepository batches;
    private final IngestionRecordDiagnosticRepository diagnostics;
    private final FileService files;
    private final MappingExecutionService mappings;
    private final IngestionRecordProcessor records;
    private final Clock clock;
    private final TransactionTemplate tx;

    public IngestionWorker(
            IngestionBatchRepository batches,
            IngestionRecordDiagnosticRepository diagnostics,
            FileService files,
            MappingExecutionService mappings,
            IngestionRecordProcessor records,
            Clock clock,
            PlatformTransactionManager tm) {
        this.batches = batches;
        this.diagnostics = diagnostics;
        this.files = files;
        this.mappings = mappings;
        this.records = records;
        this.clock = clock;
        this.tx = new TransactionTemplate(tm);
    }

    public void process(UUID tenantId, UUID id) {
        IngestionBatch batch = claim(tenantId, id);
        if (batch == null) return;
        try (var input = files.openContent(tenantId, batch.getRawSourceFileId()).content()) {
            var mapped =
                    mappings.executeBatch(
                            tenantId, batch.getMappingProfileVersionId(), input.readAllBytes());
            int created = 0, reused = 0, conflicts = 0, failed = 0;
            for (var document : mapped.documents()) {
                String outcome =
                        records.process(tenantId, id, mapped.documentType(), document).value();
                switch (outcome) {
                    case "CREATED" -> created++;
                    case "REUSED" -> reused++;
                    case "CONFLICT" -> conflicts++;
                    default -> failed++;
                }
            }
            int c = created,
                    r = reused,
                    k = conflicts,
                    f = failed,
                    total = mapped.documents().size();
            tx.executeWithoutResult(
                    s -> {
                        var current = require(tenantId, id);
                        current.complete(total, c, r, k, f, clock.instant());
                        batches.save(current);
                    });
        } catch (FileStorageException ex) {
            scheduleRetry(
                    tenantId, id, "RAW_STORAGE_UNAVAILABLE", "Raw source storage unavailable", ex);
        } catch (java.io.IOException ex) {
            scheduleRetry(tenantId, id, "RAW_READ_FAILED", "Raw source cannot be read", ex);
        } catch (RuntimeException ex) {
            permanentFailure(
                    tenantId, id, "MAPPING_FAILED", safeMessage("Ingestion mapping failed", ex));
        }
    }

    private IngestionBatch claim(UUID tenantId, UUID id) {
        return tx.execute(
                s -> {
                    var current = require(tenantId, id);
                    if (current.getStatus() == IngestionStatus.COMPLETED
                            || current.getStatus() == IngestionStatus.PARTIALLY_COMPLETED
                            || current.getStatus() == IngestionStatus.FAILED) return null;
                    if (current.getStatus() != IngestionStatus.QUEUED
                            && current.getStatus() != IngestionStatus.RETRY_WAIT) return null;
                    if (current.getNextAttemptAt() != null
                            && current.getNextAttemptAt().isAfter(clock.instant())) return null;
                    current.start(clock.instant());
                    return batches.saveAndFlush(current);
                });
    }

    private void scheduleRetry(
            UUID tenantId, UUID id, String code, String message, Throwable cause) {
        boolean retry =
                Boolean.TRUE.equals(
                        tx.execute(
                                s -> {
                                    var current = require(tenantId, id);
                                    if (current.getProcessingAttempts() >= 4) {
                                        current.fail(
                                                "MAX_ATTEMPTS_EXCEEDED",
                                                "Ingestion retry limit exceeded",
                                                clock.instant());
                                        batches.save(current);
                                        return false;
                                    }
                                    Duration delay =
                                            switch (current.getProcessingAttempts()) {
                                                case 1 -> Duration.ofMinutes(1);
                                                case 2 -> Duration.ofMinutes(10);
                                                default -> Duration.ofHours(1);
                                            };
                                    current.retry(clock.instant().plus(delay), code, message);
                                    batches.save(current);
                                    return true;
                                }));
        if (retry) throw new IngestionRetryableException(message, cause);
    }

    private void permanentFailure(UUID tenantId, UUID id, String code, String message) {
        tx.executeWithoutResult(
                s -> {
                    var current = require(tenantId, id);
                    if (diagnostics
                            .findByTenantIdAndBatchIdAndRecordOrder(tenantId, id, 0)
                            .isEmpty())
                        diagnostics.save(
                                new IngestionRecordDiagnostic(
                                        tenantId,
                                        id,
                                        0,
                                        null,
                                        "FAILED",
                                        "MAPPING",
                                        null,
                                        null,
                                        null,
                                        code,
                                        message,
                                        clock.instant()));
                    current.fail(code, message, clock.instant());
                    batches.save(current);
                });
    }

    private String safeMessage(String fallback, Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        if (message == null || message.isBlank()) message = fallback;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private IngestionBatch require(UUID tenantId, UUID id) {
        return batches.findByIdAndTenantId(id, tenantId).orElseThrow();
    }
}
