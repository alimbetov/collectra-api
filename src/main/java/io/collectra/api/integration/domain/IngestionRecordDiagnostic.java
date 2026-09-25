package io.collectra.api.integration.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_record_diagnostics")
public class IngestionRecordDiagnostic {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "ingestion_batch_id", nullable = false) private UUID batchId;
    @Column(name = "record_order", nullable = false) private int recordOrder;
    @Column(name = "document_key", length = 200) private String documentKey;
    @Column(nullable = false, length = 30) private String outcome;
    @Column(nullable = false, length = 30) private String stage;
    @Column(name = "target_type", length = 30) private String targetType;
    @Column(name = "target_id") private UUID targetId;
    @Column(name = "external_id", length = 200) private String externalId;
    @Column(name = "error_code", length = 100) private String errorCode;
    @Column(name = "safe_error_message", length = 500) private String safeErrorMessage;
    @Column(name = "field_path", length = 300) private String fieldPath;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected IngestionRecordDiagnostic() {}
    public IngestionRecordDiagnostic(UUID tenantId, UUID batchId, int order, String key,
            String outcome, String stage, String type, UUID targetId, String externalId,
            String code, String message, Instant at) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.batchId = batchId;
        this.recordOrder = order; this.documentKey = key; this.outcome = outcome; this.stage = stage;
        this.targetType = type; this.targetId = targetId; this.externalId = externalId;
        this.errorCode = code; this.safeErrorMessage = message; this.createdAt = at;
    }
    public UUID getId(){return id;} public int getRecordOrder(){return recordOrder;}
    public String getDocumentKey(){return documentKey;} public String getOutcome(){return outcome;}
    public String getStage(){return stage;} public String getTargetType(){return targetType;}
    public UUID getTargetId(){return targetId;} public String getExternalId(){return externalId;}
    public String getErrorCode(){return errorCode;} public String getSafeErrorMessage(){return safeErrorMessage;}
    public String getFieldPath(){return fieldPath;} public Instant getCreatedAt(){return createdAt;}
}
