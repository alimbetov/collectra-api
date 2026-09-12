package io.collectra.api.campaign.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "campaigns")
public class Campaign extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selection_criteria", nullable = false, columnDefinition = "jsonb")
    private JsonNode selectionCriteria;

    @Column(name = "generated_pdf_attachment", nullable = false)
    private boolean generatedPdfAttachment;

    @Column(name = "generated_pdf_attachment_required", nullable = false)
    private boolean generatedPdfAttachmentRequired;

    @Column(name = "created_by")
    private UUID createdBy;

    protected Campaign() {}

    public Campaign(
            UUID tenantId,
            String name,
            UUID templateVersionId,
            String channel,
            Instant scheduledAt,
            JsonNode selectionCriteria,
            UUID createdBy) {
        this(
                tenantId,
                name,
                templateVersionId,
                channel,
                scheduledAt,
                selectionCriteria,
                false,
                true,
                createdBy);
    }

    public Campaign(
            UUID tenantId,
            String name,
            UUID templateVersionId,
            String channel,
            Instant scheduledAt,
            JsonNode selectionCriteria,
            boolean generatedPdfAttachment,
            boolean generatedPdfAttachmentRequired,
            UUID createdBy) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.name = required(name, "name");
        this.templateVersionId = Objects.requireNonNull(templateVersionId);
        this.channel = required(channel, "channel").toUpperCase(Locale.ROOT);
        this.scheduledAt = scheduledAt;
        this.selectionCriteria = Objects.requireNonNull(selectionCriteria).deepCopy();
        this.generatedPdfAttachment = generatedPdfAttachment;
        this.generatedPdfAttachmentRequired =
                generatedPdfAttachment && generatedPdfAttachmentRequired;
        this.createdBy = createdBy;
        this.status = CampaignStatus.DRAFT;
    }

    public void configureGeneratedPdfAttachment(boolean enabled, boolean required) {
        if (status != CampaignStatus.DRAFT) {
            throw new IllegalStateException(
                    "Generated PDF attachment can only be configured for draft campaign");
        }
        generatedPdfAttachment = enabled;
        generatedPdfAttachmentRequired = enabled && required;
    }

    public void activate() {
        if (status != CampaignStatus.DRAFT) {
            throw new IllegalStateException("Only draft campaign can be activated");
        }
        status = CampaignStatus.ACTIVE;
    }

    public void archive() {
        status = CampaignStatus.ARCHIVED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public JsonNode getSelectionCriteria() {
        return selectionCriteria.deepCopy();
    }

    public boolean isGeneratedPdfAttachment() {
        return generatedPdfAttachment;
    }

    public boolean isGeneratedPdfAttachmentRequired() {
        return generatedPdfAttachmentRequired;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
