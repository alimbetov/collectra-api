package io.collectra.api.integration.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_batches")
public class IngestionBatch {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "integration_source_id", nullable = false) private UUID integrationSourceId;
    @Column(name = "service_client_id", nullable = false) private UUID serviceClientId;
    @Column(name = "source_code", nullable = false, length = 100) private String sourceCode;
    @Column(name = "idempotency_key", nullable = false, length = 200) private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "request_id", length = 200) private String requestId;
    @Column(name = "raw_source_file_id", nullable = false) private UUID rawSourceFileId;
    @Column(name = "source_schema_version_id", nullable = false) private UUID sourceSchemaVersionId;
    @Column(name = "mapping_profile_version_id", nullable = false) private UUID mappingProfileVersionId;
    @Column(name = "mapping_config_sha256", length = 64) private String mappingConfigSha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private IngestionStatus status;
    @Column(name = "content_type", nullable = false, length = 150) private String contentType;
    @Column(name = "record_count", nullable = false) private int recordCount;
    @Column(name = "created_count", nullable = false) private int createdCount;
    @Column(name = "reused_count", nullable = false) private int reusedCount;
    @Column(name = "conflict_count", nullable = false) private int conflictCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;
    @Column(name = "processing_attempts", nullable = false) private int processingAttempts;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "processing_started_at") private Instant processingStartedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "error_code", length = 100) private String errorCode;
    @Column(name = "safe_error_message", length = 500) private String safeErrorMessage;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", nullable = false, columnDefinition = "jsonb") private String contextJson;
    @Version private long version;

    protected IngestionBatch() {}

    public IngestionBatch(UUID id, UUID tenantId, UUID sourceId, UUID clientId, String sourceCode,
            String key, String hash, String requestId, UUID fileId, UUID schemaVersionId,
            UUID mappingVersionId, String contentType, Instant receivedAt, String contextJson) {
        this.id = id; this.tenantId = tenantId; this.integrationSourceId = sourceId;
        this.serviceClientId = clientId; this.sourceCode = sourceCode; this.idempotencyKey = key;
        this.requestHash = hash; this.requestId = requestId; this.rawSourceFileId = fileId;
        this.sourceSchemaVersionId = schemaVersionId; this.mappingProfileVersionId = mappingVersionId;
        this.contentType = contentType; this.receivedAt = receivedAt; this.contextJson = contextJson;
        this.status = IngestionStatus.QUEUED; this.nextAttemptAt = receivedAt;
    }

    public void start(Instant now) {
        if (status != IngestionStatus.QUEUED && status != IngestionStatus.RETRY_WAIT)
            throw new IllegalStateException("Ingestion is not claimable");
        status = IngestionStatus.PROCESSING; processingAttempts++; processingStartedAt = now;
        nextAttemptAt = null; errorCode = null; safeErrorMessage = null;
    }
    public void retry(Instant next, String code, String message) {
        requireProcessing(); status = IngestionStatus.RETRY_WAIT; nextAttemptAt = next;
        errorCode = code; safeErrorMessage = safe(message);
    }
    public void recover(Instant next) {
        requireProcessing(); status = IngestionStatus.RETRY_WAIT; nextAttemptAt = next;
        errorCode = "WORKER_RECOVERED"; safeErrorMessage = "Recovered stale ingestion processing";
    }
    public void complete(int total, int created, int reused, int conflicts, int failed, Instant now) {
        requireProcessing(); recordCount = total; createdCount = created; reusedCount = reused;
        conflictCount = conflicts; failedCount = failed;
        status = conflicts + failed == 0 ? IngestionStatus.COMPLETED
                : created + reused > 0 ? IngestionStatus.PARTIALLY_COMPLETED : IngestionStatus.FAILED;
        completedAt = now; nextAttemptAt = null;
    }
    public void fail(String code, String message, Instant now) {
        status = IngestionStatus.FAILED; errorCode = code; safeErrorMessage = safe(message);
        completedAt = now; nextAttemptAt = null;
    }
    private void requireProcessing() {
        if (status != IngestionStatus.PROCESSING) throw new IllegalStateException("Ingestion is not processing");
    }
    private String safe(String value) {
        if (value == null) return null; return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;}
    public UUID getIntegrationSourceId(){return integrationSourceId;} public UUID getServiceClientId(){return serviceClientId;}
    public String getSourceCode(){return sourceCode;} public String getIdempotencyKey(){return idempotencyKey;}
    public String getRequestHash(){return requestHash;} public String getRequestId(){return requestId;}
    public UUID getRawSourceFileId(){return rawSourceFileId;} public UUID getSourceSchemaVersionId(){return sourceSchemaVersionId;}
    public UUID getMappingProfileVersionId(){return mappingProfileVersionId;} public String getMappingConfigSha256(){return mappingConfigSha256;}
    public IngestionStatus getStatus(){return status;} public String getContentType(){return contentType;}
    public int getRecordCount(){return recordCount;} public int getCreatedCount(){return createdCount;}
    public int getReusedCount(){return reusedCount;} public int getConflictCount(){return conflictCount;}
    public int getFailedCount(){return failedCount;} public int getProcessingAttempts(){return processingAttempts;}
    public Instant getNextAttemptAt(){return nextAttemptAt;} public Instant getReceivedAt(){return receivedAt;}
    public Instant getProcessingStartedAt(){return processingStartedAt;} public Instant getCompletedAt(){return completedAt;}
    public String getErrorCode(){return errorCode;} public String getSafeErrorMessage(){return safeErrorMessage;}
}
