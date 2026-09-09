package io.collectra.api.importing.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;

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

    protected ImportBatch() {}
    public ImportBatch(UUID tenantId, String idempotencyKey, String requestHash,
            UUID mappingProfileVersionId, UUID templateVersionId) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash; this.mappingProfileVersionId = mappingProfileVersionId;
        this.templateVersionId = templateVersionId; this.status = ImportBatchStatus.PROCESSING;
    }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public ImportBatchStatus getStatus() { return status; }
    public int getDocumentCount() { return documentCount; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getFailedAt() { return failedAt; }
    public void accepted(int count) { this.documentCount = count; this.status = ImportBatchStatus.ACCEPTED; }
    public void failed(String code, String message) {
        this.documentCount = 0;
        this.status = ImportBatchStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = truncate(message, 1000);
        this.failedAt = Instant.now();
    }

    private String truncate(String value, int limit) {
        String safe = value == null || value.isBlank() ? "Import processing failed" : value;
        return safe.length() <= limit ? safe : safe.substring(0, limit);
    }
}
