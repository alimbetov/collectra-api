package io.collectra.api.importing.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
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
    public void accepted(int count) { this.documentCount = count; this.status = ImportBatchStatus.ACCEPTED; }
}
