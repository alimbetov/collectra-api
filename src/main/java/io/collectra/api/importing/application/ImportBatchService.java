package io.collectra.api.importing.application;

import io.collectra.api.document.application.GenerationJobService;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.importing.domain.ImportBatch;
import io.collectra.api.importing.domain.ImportBatchDocument;
import io.collectra.api.importing.infrastructure.ImportBatchDocumentRepository;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchService {
    private final ImportBatchRepository batches;
    private final ImportBatchDocumentRepository documents;
    private final MappingExecutionService mappings;
    private final GenerationJobService jobs;

    public ImportBatchService(ImportBatchRepository batches,
            ImportBatchDocumentRepository documents, MappingExecutionService mappings,
            GenerationJobService jobs) {
        this.batches = batches; this.documents = documents; this.mappings = mappings; this.jobs = jobs;
    }

    @Transactional
    public BatchResult create(UUID tenantId, String idempotencyKey, UUID mappingProfileVersionId,
            UUID templateVersionId, byte[] input, Set<OutputFormat> formats) {
        String key = validateKey(idempotencyKey);
        String requestHash = requestHash(input, mappingProfileVersionId, templateVersionId, formats);
        var existing = batches.findByTenantIdAndIdempotencyKey(tenantId, key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash))
                throw new IllegalArgumentException("Idempotency key was already used with another request");
            return result(existing.get(), true);
        }
        ImportBatch batch = batches.save(new ImportBatch(tenantId, key, requestHash,
                mappingProfileVersionId, templateVersionId));
        var mapped = mappings.executeBatch(tenantId, mappingProfileVersionId, input);
        for (var document : mapped.documents()) {
            var single = new MappingExecutionService.MappingResult(mapped.mappingProfileId(),
                    mapped.sourceSchemaVersionId(), mapped.documentType(),
                    document.normalizedPayload(), mapped.mappingConfigSha256());
            var job = jobs.createMapped(tenantId, mappingProfileVersionId, templateVersionId,
                    single, formats);
            documents.save(new ImportBatchDocument(batch.getId(), document.order(),
                    document.documentKey(), job.getId()));
        }
        batch.accepted(mapped.documents().size());
        return result(batch, false);
    }

    @Transactional(readOnly = true)
    public BatchResult get(UUID tenantId, UUID batchId) {
        ImportBatch batch = batches.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Import batch not found"));
        return result(batch, false);
    }

    private BatchResult result(ImportBatch batch, boolean replayed) {
        List<DocumentResult> values = documents.findAllByImportBatchIdOrderByDocumentOrder(batch.getId())
                .stream().map(value -> new DocumentResult(value.getDocumentOrder(),
                        value.getDocumentKey(), value.getGenerationJobId())).toList();
        return new BatchResult(batch.getId(), batch.getStatus().name(), batch.getDocumentCount(),
                replayed, values);
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

    public record DocumentResult(int order, String documentKey, UUID generationJobId) {}
    public record BatchResult(UUID batchId, String status, int documentCount, boolean replayed,
            List<DocumentResult> documents) {}
}
