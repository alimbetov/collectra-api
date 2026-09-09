package io.collectra.api.document.domain;

import com.fasterxml.jackson.databind.JsonNode;

import io.collectra.api.shared.persistence.AuditableEntity;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

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
        this.tenantId = tenantId;
        this.documentType = documentType;
        this.mappingProfileId = mappingProfileId;
        this.templateVersionId = templateVersionId;
        this.inputFileId = inputFileId;
        this.normalizedPayload = normalizedPayload;
        this.outputFormats =
                outputFormats.stream()
                        .sorted()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.joining(","));
        this.status = GenerationJobStatus.PENDING;
    }

    public GenerationJob(
            UUID tenantId, String documentType, UUID sourceSchemaVersionId,
            UUID mappingProfileId, UUID templateVersionId, UUID inputFileId,
            JsonNode normalizedPayload, java.util.Set<OutputFormat> outputFormats,
            String mappingConfigSha256, String templateConfigSha256) {
        this(tenantId, documentType, mappingProfileId, templateVersionId, inputFileId,
                normalizedPayload, outputFormats);
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

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public UUID getSourceSchemaVersionId() { return sourceSchemaVersionId; }
    public UUID getMappingProfileId() { return mappingProfileId; }
    public String getMappingConfigSha256() { return mappingConfigSha256; }
    public String getTemplateConfigSha256() { return templateConfigSha256; }

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

    public java.util.Set<OutputFormat> getOutputFormats() {
        return java.util.Arrays.stream(outputFormats.split(","))
                .filter(value -> !value.isBlank())
                .map(OutputFormat::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void start(String step) {
        if (status != GenerationJobStatus.PENDING)
            throw new IllegalStateException("Job is not pending");
        status = GenerationJobStatus.PROCESSING;
        currentStep = step;
        startedAt = Instant.now();
        attemptCount++;
        errorCode = null;
        errorMessage = null;
    }

    public void step(String step) {
        if (status != GenerationJobStatus.PROCESSING)
            throw new IllegalStateException("Job is not processing");
        currentStep = step;
    }

    public void retry(String code, String message) {
        if (status != GenerationJobStatus.PROCESSING)
            throw new IllegalStateException("Job is not processing");
        status = GenerationJobStatus.PENDING;
        currentStep = null;
        errorCode = code;
        errorMessage = message;
    }

    public void complete() {
        if (status != GenerationJobStatus.PROCESSING)
            throw new IllegalStateException("Job is not processing");
        status = GenerationJobStatus.COMPLETED;
        currentStep = null;
        completedAt = Instant.now();
    }

    public void fail(String code, String message) {
        status = GenerationJobStatus.FAILED;
        errorCode = code;
        errorMessage = message;
        completedAt = Instant.now();
    }
}
