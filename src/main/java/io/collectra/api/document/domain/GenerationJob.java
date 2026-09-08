package io.collectra.api.document.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "generation_jobs")
public class GenerationJob extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "document_type", nullable = false, length = 50) private String documentType;
    @Column(name = "mapping_profile_id") private UUID mappingProfileId;
    @Column(name = "template_version_id", nullable = false) private UUID templateVersionId;
    @Column(name = "input_file_id") private UUID inputFileId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "normalized_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode normalizedPayload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private GenerationJobStatus status;
    @Column(name = "current_step", length = 40) private String currentStep;
    @Column(name = "error_code", length = 60) private String errorCode;
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    protected GenerationJob() {}
    public GenerationJob(UUID tenantId, String documentType, UUID mappingProfileId,
            UUID templateVersionId, UUID inputFileId, JsonNode normalizedPayload) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.documentType = documentType;
        this.mappingProfileId = mappingProfileId; this.templateVersionId = templateVersionId;
        this.inputFileId = inputFileId; this.normalizedPayload = normalizedPayload;
        this.status = GenerationJobStatus.PENDING;
    }
    public UUID getId() { return id; } public UUID getTenantId() { return tenantId; }
    public JsonNode getNormalizedPayload() { return normalizedPayload; }
    public GenerationJobStatus getStatus() { return status; }
    public void start(String step) { if (status != GenerationJobStatus.PENDING) throw new IllegalStateException("Job is not pending"); status = GenerationJobStatus.PROCESSING; currentStep = step; startedAt = Instant.now(); }
    public void complete() { if (status != GenerationJobStatus.PROCESSING) throw new IllegalStateException("Job is not processing"); status = GenerationJobStatus.COMPLETED; currentStep = null; completedAt = Instant.now(); }
    public void fail(String code, String message) { status = GenerationJobStatus.FAILED; errorCode = code; errorMessage = message; completedAt = Instant.now(); }
}
