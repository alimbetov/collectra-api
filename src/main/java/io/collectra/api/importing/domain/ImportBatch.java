package io.collectra.api.importing.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_batches")
public class ImportBatch extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "mapping_profile_version_id", nullable = false) private UUID mappingProfileVersionId;
    @Column(name = "template_version_id", nullable = false) private UUID templateVersionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ImportBatchStatus status;
    @Column(name = "document_count", nullable = false) private int documentCount;
    @Column(name = "error_code", length = 60) private String errorCode;
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "failed_at") private Instant failedAt;
    @Column(nullable = false, length = 30) private String source = "LEGACY_IMPORT";
    @Column(name = "source_schema_version_id") private UUID sourceSchemaVersionId;
    @Column(name = "raw_source_file_id") private UUID rawSourceFileId;
    @Column(name = "processing_started_at") private Instant processingStartedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "processing_attempts", nullable = false) private int processingAttempts = 1;
    @Column(name = "record_count", nullable = false) private int recordCount;
    @Column(name = "created_count", nullable = false) private int createdCount;
    @Column(name = "reused_count", nullable = false) private int reusedCount;
    @Column(name = "conflict_count", nullable = false) private int conflictCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;

    protected ImportBatch() {}

    public ImportBatch(
            UUID tenantId,
            String idempotencyKey,
            String requestHash,
            UUID mappingProfileVersionId,
            UUID templateVersionId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.mappingProfileVersionId = mappingProfileVersionId;
        this.templateVersionId = templateVersionId;
        this.status = ImportBatchStatus.PROCESSING;
        this.processingStartedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public UUID getMappingProfileVersionId() {
        return mappingProfileVersionId;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public String getSource() {
        return source;
    }

    public UUID getSourceSchemaVersionId() {
        return sourceSchemaVersionId;
    }

    public UUID getRawSourceFileId() {
        return rawSourceFileId;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public int getProcessingAttempts() {
        return processingAttempts;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public int getReusedCount() {
        return reusedCount;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void accepted(int count) {
        this.documentCount = count;
        this.recordCount = count;
        this.createdCount = count;
        this.status = ImportBatchStatus.ACCEPTED;
        this.completedAt = Instant.now();
    }

    public void failed(String code, String message) {
        this.documentCount = 0;
        this.status = ImportBatchStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = truncate(message, 1000);
        this.failedAt = Instant.now();
        this.completedAt = this.failedAt;
        this.failedCount = Math.max(this.recordCount, 1);
    }

    private String truncate(String value, int limit) {
        String safe = value == null || value.isBlank() ? "Import processing failed" : value;
        return safe.length() <= limit ? safe : safe.substring(0, limit);
    }
}
