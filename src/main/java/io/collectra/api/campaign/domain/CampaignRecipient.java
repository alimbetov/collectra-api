package io.collectra.api.campaign.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
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
        name = "campaign_recipients",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_campaign_recipient_run_customer_invoice",
                        columnNames = {"run_id", "customer_id", "invoice_id"}))
public class CampaignRecipient extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(length = 500)
    private String destination;

    @Column(length = 16)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignRecipientStatus status;

    @Column(name = "skip_reason", length = 40)
    private String skipReason;

    protected CampaignRecipient() {}

    public CampaignRecipient(
            UUID tenantId,
            UUID campaignId,
            UUID runId,
            UUID customerId,
            UUID invoiceId,
            String channel,
            String destination,
            String locale) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.campaignId = Objects.requireNonNull(campaignId);
        this.runId = Objects.requireNonNull(runId);
        this.customerId = Objects.requireNonNull(customerId);
        this.invoiceId = invoiceId;
        this.channel = required(channel);
        this.destination = trim(destination);
        this.locale = trim(locale);
        this.status = CampaignRecipientStatus.SNAPSHOT;
    }

    public void eligible() {
        status = CampaignRecipientStatus.ELIGIBLE;
        skipReason = null;
    }

    public void skip(String reason) {
        status = CampaignRecipientStatus.SKIPPED;
        skipReason = required(reason);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getChannel() {
        return channel;
    }

    public String getDestination() {
        return destination;
    }

    public String getLocale() {
        return locale;
    }

    public CampaignRecipientStatus getStatus() {
        return status;
    }

    public String getSkipReason() {
        return skipReason;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
