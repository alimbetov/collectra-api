package io.collectra.api.importing.application;

import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.importing.domain.ImportBatch;
import io.collectra.api.importing.infrastructure.ImportBatchDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchService {
    private final ImportBatchDocumentRepository documents;
    private final ImportBatchReservationService reservations;
    private final ImportBatchProcessingService processing;

    public ImportBatchService(ImportBatchDocumentRepository documents,
            ImportBatchReservationService reservations, ImportBatchProcessingService processing) {
        this.documents = documents; this.reservations = reservations; this.processing = processing;
    }

    public BatchResult create(UUID tenantId, String idempotencyKey, UUID mappingProfileVersionId,
            UUID templateVersionId, byte[] input, Set<OutputFormat> formats) {
        String key = validateKey(idempotencyKey);
        String requestHash = requestHash(input, mappingProfileVersionId, templateVersionId, formats);
        ImportBatchReservationService.Reservation reservation;
        try {
            reservation = reservations.reserve(tenantId, key, requestHash,
                    mappingProfileVersionId, templateVersionId);
        } catch (DataIntegrityViolationException ex) {
            reservation = reservations.existing(tenantId, key, requestHash);
        }
        ImportBatch batch = reservation.batch();
        if (!reservation.created()) return result(batch, true);
        try {
            processing.process(tenantId, batch.getId(), mappingProfileVersionId,
                    templateVersionId, input, formats);
        } catch (RuntimeException ex) {
            String code = failureCode(ex);
            String message = safeMessage(ex, code);
            reservations.fail(tenantId, batch.getId(), code, message);
            throw new ImportBatchFailedException(batch.getId(), code, message, ex);
        }
        return result(reservations.get(tenantId, batch.getId()), false);
    }

    @Transactional(readOnly = true)
    public BatchResult get(UUID tenantId, UUID batchId) {
        return result(reservations.get(tenantId, batchId), false);
    }

    private BatchResult result(ImportBatch batch, boolean replayed) {
        List<DocumentResult> values = documents.findAllByImportBatchIdOrderByDocumentOrder(batch.getId())
                .stream().map(value -> new DocumentResult(value.getDocumentOrder(),
                        value.getDocumentKey(), value.getGenerationJobId())).toList();
        Failure failure = batch.getErrorCode() == null ? null : new Failure(batch.getErrorCode(),
                batch.getErrorMessage(), batch.getFailedAt());
        return new BatchResult(batch.getId(), batch.getStatus().name(), batch.getDocumentCount(),
                replayed, values, failure);
    }

    private String validateKey(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Idempotency-Key header is required");
        String key = value.trim();
        if (key.length() > 128) throw new IllegalArgumentException("Idempotency-Key is too long");
        return key;
    }

    private String requestHash(byte[] input, UUID mappingId, UUID templateId,
            Set<OutputFormat> formats) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(input);
            digest.update(mappingId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update(templateId.toString().getBytes(StandardCharsets.UTF_8));
            formats.stream().sorted().map(Enum::name)
                    .forEach(value -> digest.update(value.getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String failureCode(RuntimeException ex) {
        if (ex instanceof MappingValidationException) return "MAPPING_VALIDATION_FAILED";
        if (ex instanceof IllegalArgumentException) return "INVALID_IMPORT_INPUT";
        return "IMPORT_PROCESSING_FAILED";
    }

    private String safeMessage(RuntimeException ex, String code) {
        if ("IMPORT_PROCESSING_FAILED".equals(code)) return "Import processing failed";
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Import processing failed" : ex.getMessage();
    }

    public record DocumentResult(int order, String documentKey, UUID generationJobId) {}
    public record Failure(String code, String message, java.time.Instant failedAt) {}
    public record BatchResult(UUID batchId, String status, int documentCount, boolean replayed,
            List<DocumentResult> documents, Failure failure) {}
}
