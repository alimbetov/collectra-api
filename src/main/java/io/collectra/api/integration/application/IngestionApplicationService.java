package io.collectra.api.integration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.collectra.api.file.application.*;
import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.importing.domain.DefinitionStatus;
import io.collectra.api.importing.infrastructure.*;
import io.collectra.api.integration.domain.*;
import io.collectra.api.integration.infrastructure.*;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class IngestionApplicationService {
    public static final String EVENT_TYPE = "INTEGRATION_INGESTION_REQUESTED";

    private final IntegrationSourceRepository sources;
    private final IngestionBatchRepository batches;
    private final SourceSchemaRepository schemas;
    private final MappingProfileRepository mappings;
    private final FileService files;
    private final IngestionReservationWriter writer;
    private final ObjectMapper json;

    public IngestionApplicationService(IntegrationSourceRepository sources, IngestionBatchRepository batches,
            SourceSchemaRepository schemas, MappingProfileRepository mappings, FileService files,
            IngestionReservationWriter writer, ObjectMapper json) {
        this.sources = sources; this.batches = batches; this.schemas = schemas; this.mappings = mappings;
        this.files = files; this.writer = writer; this.json = json;
    }

    public Reservation reserve(UUID tenantId, UUID serviceClientId, String sourceCode,
            String idempotencyKey, String requestId, String contentType, byte[] content) {
        String code = required(sourceCode, "SOURCE_CODE_REQUIRED");
        String key = required(idempotencyKey, "IDEMPOTENCY_KEY_REQUIRED");
        if (key.length() > 200) throw new IngestionRequestException("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key is too long");
        IntegrationSource source = sources.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new NoSuchElementException("Integration source not found"));
        if (source.getStatus() != IntegrationSourceStatus.ACTIVE)
            throw new IngestionConflictException("SOURCE_NOT_ACTIVE", "Integration source is not active");
        if (!source.getServiceClientId().equals(serviceClientId))
            throw new NoSuchElementException("Integration source not found");

        String hash = sha256(content);
        var existing = findExisting(tenantId, source.getId(), serviceClientId, key);
        if (existing.isPresent()) return replay(existing.get(), hash);

        var schema = schemas.findAllByDefinitionIdOrderBySchemaVersionDesc(source.getSourceSchemaDefinitionId()).stream()
                .filter(x -> x.getTenantId().equals(tenantId) && x.getStatus() == DefinitionStatus.PUBLISHED)
                .findFirst().orElseThrow(() -> new IngestionConflictException("SOURCE_SCHEMA_NOT_READY", "Published source schema not found"));
        var mapping = mappings.findAllByDefinitionIdOrderByProfileVersionDesc(source.getMappingProfileDefinitionId()).stream()
                .filter(x -> x.getTenantId().equals(tenantId) && x.getStatus() == DefinitionStatus.PUBLISHED
                        && x.getSourceSchemaId().equals(schema.getId()))
                .findFirst().orElseThrow(() -> new IngestionConflictException("MAPPING_NOT_READY", "Published coherent mapping not found"));

        UUID ingestionId = UUID.randomUUID();
        FileMetadata raw = files.upload(new UploadFileCommand(tenantId, null, FileCategory.IMPORT_SOURCE,
                "ingestion-" + ingestionId, contentType, content.length, new ByteArrayInputStream(content), serviceClientId));
        ObjectNode context = json.createObjectNode();
        context.put("sourceCode", code); context.put("idempotencyKey", key);
        if (requestId != null && !requestId.isBlank()) context.put("requestId", requestId);
        context.put("sourceSchemaVersionId", schema.getId().toString());
        context.put("mappingProfileVersionId", mapping.getId().toString());

        try {
            IngestionBatch created = writer.create(ingestionId, tenantId, source.getId(), serviceClientId,
                    code, key, hash, requestId, raw.fileId(), schema.getId(), mapping.getId(), contentType, context.toString());
            return response(created, false);
        } catch (DataIntegrityViolationException race) {
            safeDeleteRaw(tenantId, raw.fileId());
            IngestionBatch winner = findExisting(tenantId, source.getId(), serviceClientId, key)
                    .orElseThrow(() -> race);
            return replay(winner, hash);
        } catch (RuntimeException failure) {
            safeDeleteRaw(tenantId, raw.fileId());
            throw failure;
        }
    }

    public Reservation status(UUID tenantId, UUID id) {
        return response(batches.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Ingestion batch not found")), false);
    }

    private java.util.Optional<IngestionBatch> findExisting(UUID tenantId, UUID sourceId, UUID clientId, String key) {
        return batches.findByTenantIdAndIntegrationSourceIdAndServiceClientIdAndIdempotencyKey(tenantId, sourceId, clientId, key);
    }
    private Reservation replay(IngestionBatch batch, String hash) {
        if (!batch.getRequestHash().equals(hash))
            throw new IngestionConflictException("IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key was already used with different request body");
        return response(batch, true);
    }
    private void safeDeleteRaw(UUID tenantId, UUID fileId) {
        try { files.delete(tenantId, fileId); } catch (RuntimeException ignored) { }
    }
    private String required(String value, String code) {
        if (value == null || value.isBlank()) throw new IngestionRequestException(code, "Required ingestion value is missing");
        return value.trim();
    }
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private Reservation response(IngestionBatch b, boolean replayed) {
        return new Reservation(b.getId(), b.getSourceCode(), b.getStatus().name(), replayed, b.getRequestId(),
                b.getSourceSchemaVersionId(), b.getMappingProfileVersionId(), b.getReceivedAt(),
                b.getRecordCount(), b.getCreatedCount(), b.getReusedCount(), b.getConflictCount(),
                b.getFailedCount(), b.getErrorCode(), b.getSafeErrorMessage());
    }
    public record Reservation(UUID ingestionId, String sourceCode, String status, boolean replayed,
            String requestId, UUID sourceSchemaVersionId, UUID mappingProfileVersionId,
            java.time.Instant receivedAt, int recordCount, int createdCount, int reusedCount,
            int conflictCount, int failedCount, String errorCode, String errorMessage) {}
}
