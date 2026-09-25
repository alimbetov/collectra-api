package io.collectra.api.importing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_record_diagnostic")
public class ImportRecordDiagnostic {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "record_number", nullable = false)
    private int recordNumber;

    @Column(name = "record_order", nullable = false)
    private int recordOrder;

    @Column(name = "document_key", length = 300)
    private String documentKey;

    @Column(nullable = false, length = 60)
    private String stage;

    @Column(name = "field_path", length = 300)
    private String fieldPath;

    @Column(name = "error_code", nullable = false, length = 100)
    private String errorCode;

    @Column(name = "safe_detail", nullable = false, length = 500)
    private String safeDetail;

    @Column(name = "masked_source_value", length = 500)
    private String maskedSourceValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ImportRecordDiagnostic() {}

    public ImportRecordDiagnostic(
            UUID tenantId,
            UUID importId,
            int recordNumber,
            int recordOrder,
            String documentKey,
            String stage,
            String fieldPath,
            String errorCode,
            String safeDetail,
            String maskedSourceValue,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.importId = importId;
        this.recordNumber = recordNumber;
        this.recordOrder = recordOrder;
        this.documentKey = documentKey;
        this.stage = stage;
        this.fieldPath = fieldPath;
        this.errorCode = errorCode;
        this.safeDetail = safeDetail;
        this.maskedSourceValue = maskedSourceValue;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getImportId() { return importId; }
    public int getRecordNumber() { return recordNumber; }
    public int getRecordOrder() { return recordOrder; }
    public String getDocumentKey() { return documentKey; }
    public String getStage() { return stage; }
    public String getFieldPath() { return fieldPath; }
    public String getErrorCode() { return errorCode; }
    public String getSafeDetail() { return safeDetail; }
    public String getMaskedSourceValue() { return maskedSourceValue; }
    public Instant getCreatedAt() { return createdAt; }
}
