package io.collectra.api.document.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "generation_jobs")
public class GenerationJob extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "mapping_profile_id")
    private UUID mappingProfileId;

    @Column(name = "source_schema_version_id")
    private UUID sourceSchemaVersionId;

    @Column(name = "mapping_config_sha256", length = 64)
    private String mappingConfigSha256;

    @Column(name = "template_config_sha256", length = 64)
    private String templateConfigSha256;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Column(name = "input_file_id")
    private UUID inputFileId;

    @Column(name = "output_formats", nullable = false, length = 30)
    private String outputFormats;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode normalizedPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GenerationJobStatus status;

    @Column(name = "current_step", length = 40)
    private String currentStep;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected GenerationJob() {}

    public GenerationJob(
            UUID tenantId,
            String documentType,
            UUID mappingProfileId,
            UUID templateVersionId,
            UUID inputFileId,
            JsonNode normalizedPayload) {
        this(
                tenantId,
                documentType,
                mappingProfileId,
                templateVersionId,
                inputFileId,
                normalizedPayload,
                java.util.Set.of(OutputFormat.HTML, OutputFormat.PDF));
    }

    public GenerationJob(
            UUID tenantId,
            String documentType,
            UUID mappingProfileId,
            UUID templateVersionId,
            UUID inputFileId,
            JsonNode normalizedPayload,
            java.util.Set<OutputFormat> outputFormats) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.documentType = Objects.requireNonNull(documentType, "documentType is required");
        this.mappingProfileId = mappingProfileId;
        this.templateVersionId =
                Objects.requireNonNull(templateVersionId, "templateVersionId is required");
        this.inputFileId = inputFileId;
        this.normalizedPayload =
                Objects.requireNonNull(normalizedPayload, "normalizedPayload is required");
        if (outputFormats == null || outputFormats.isEmpty()) {
            throw new IllegalArgumentException("At least one output format is required");
        }
        this.outputFormats =
                outputFormats.stream()
                        .sorted()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.joining(","));
        this.status = GenerationJobStatus.PENDING;
    }

    public GenerationJob(
            UUID tenantId,
            String documentType,
            UUID sourceSchemaVersionId,
            UUID mappingProfileId,
            UUID templateVersionId,
            UUID inputFileId,
            JsonNode normalizedPayload,
            java.util.Set<OutputFormat> outputFormats,
            String mappingConfigSha256,
            String templateConfigSha256) {
        this(
                tenantId,
                documentType,
                mappingProfileId,
                templateVersionId,
                inputFileId,
                normalizedPayload,
                outputFormats);
        this.sourceSchemaVersionId = sourceSchemaVersionId;
        this.mappingConfigSha256 = mappingConfigSha256;
        this.templateConfigSha256 = templateConfigSha256;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public UUID getSourceSchemaVersionId() {
        return sourceSchemaVersionId;
    }

    public UUID getMappingProfileId() {
        return mappingProfileId;
    }

    public String getMappingConfigSha256() {
        return mappingConfigSha256;
    }

    public String getTemplateConfigSha256() {
        return templateConfigSha256;
    }

    public JsonNode getNormalizedPayload() {
        return normalizedPayload;
    }

    public GenerationJobStatus getStatus() {
        return status;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public java.util.Set<OutputFormat> getOutputFormats() {
        return java.util.Arrays.stream(outputFormats.split(","))
                .filter(value -> !value.isBlank())
                .map(OutputFormat::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void start(String step, Instant now) {
        if (status != GenerationJobStatus.PENDING) {
            throw new IllegalStateException("Job is not pending");
        }
        status = GenerationJobStatus.PROCESSING;
        currentStep = Objects.requireNonNull(step, "step is required");
        startedAt = Objects.requireNonNull(now, "now is required");
        completedAt = null;
        attemptCount++;
        errorCode = null;
        errorMessage = null;
    }

    public void step(String step) {
        if (status != GenerationJobStatus.PROCESSING) {
            throw new IllegalStateException("Job is not processing");
        }
        currentStep = Objects.requireNonNull(step, "step is required");
    }

    public void retry(String code, String message) {
        if (status != GenerationJobStatus.PROCESSING) {
            throw new IllegalStateException("Job is not processing");
        }
        status = GenerationJobStatus.PENDING;
        currentStep = null;
        startedAt = null;
        errorCode = code;
        errorMessage = message;
    }

    public void complete(Instant now) {
        if (status != GenerationJobStatus.PROCESSING) {
            throw new IllegalStateException("Job is not processing");
        }
        status = GenerationJobStatus.COMPLETED;
        currentStep = null;
        completedAt = Objects.requireNonNull(now, "now is required");
    }

    public boolean fail(String code, String message, Instant now) {
        if (status == GenerationJobStatus.FAILED) {
            return false;
        }
        if (status == GenerationJobStatus.COMPLETED) {
            return false;
        }
        status = GenerationJobStatus.FAILED;
        currentStep = null;
        errorCode = code;
        errorMessage = message;
        completedAt = Objects.requireNonNull(now, "now is required");
        return true;
    }
}
