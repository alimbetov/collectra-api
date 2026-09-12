package io.collectra.api.campaign.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import io.collectra.api.template.application.TemplateLocaleResolutionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "campaign_run_template_bindings",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_campaign_run_template_binding_locale",
                        columnNames = {"campaign_run_id", "requested_locale"}))
public class CampaignRunTemplateBinding extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_run_id", nullable = false)
    private UUID campaignRunId;

    @Column(name = "requested_locale", nullable = false, length = 35)
    private String requestedLocale;

    @Column(name = "resolved_locale", nullable = false, length = 35)
    private String resolvedLocale;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_source", nullable = false, length = 30)
    private TemplateLocaleResolutionSource resolutionSource;

    protected CampaignRunTemplateBinding() {}

    public CampaignRunTemplateBinding(
            UUID tenantId,
            UUID campaignRunId,
            String requestedLocale,
            String resolvedLocale,
            UUID templateVersionId,
            TemplateLocaleResolutionSource resolutionSource) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.campaignRunId = Objects.requireNonNull(campaignRunId, "campaignRunId is required");
        this.requestedLocale = required(requestedLocale, "requestedLocale");
        this.resolvedLocale = required(resolvedLocale, "resolvedLocale");
        this.templateVersionId =
                Objects.requireNonNull(templateVersionId, "templateVersionId is required");
        this.resolutionSource =
                Objects.requireNonNull(resolutionSource, "resolutionSource is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCampaignRunId() { return campaignRunId; }
    public String getRequestedLocale() { return requestedLocale; }
    public String getResolvedLocale() { return resolvedLocale; }
    public UUID getTemplateVersionId() { return templateVersionId; }
    public TemplateLocaleResolutionSource getResolutionSource() { return resolutionSource; }
}
