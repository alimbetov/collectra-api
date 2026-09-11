package io.collectra.api.campaign.domain;

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

@Entity
@Table(name = "campaign_runs")
public class CampaignRun extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignRunStatus status;

    @Column(name = "prepared_at")
    private Instant preparedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CampaignRun() {}

    public CampaignRun(UUID tenantId, UUID campaignId) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.campaignId = Objects.requireNonNull(campaignId);
        this.status = CampaignRunStatus.PREPARING;
    }

    public void ready() {
        require(CampaignRunStatus.PREPARING);
        status = CampaignRunStatus.READY;
        preparedAt = Instant.now();
    }

    public void start() {
        require(CampaignRunStatus.READY);
        status = CampaignRunStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void complete() {
        require(CampaignRunStatus.RUNNING);
        status = CampaignRunStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void fail() {
        if (status != CampaignRunStatus.RUNNING && status != CampaignRunStatus.PREPARING) {
            throw new IllegalStateException("Campaign run cannot fail from " + status);
        }
        status = CampaignRunStatus.FAILED;
        completedAt = Instant.now();
    }

    public void cancel() {
        if (status == CampaignRunStatus.COMPLETED || status == CampaignRunStatus.FAILED) {
            throw new IllegalStateException("Finished campaign run cannot be cancelled");
        }
        status = CampaignRunStatus.CANCELLED;
        completedAt = Instant.now();
    }

    private void require(CampaignRunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected " + expected + " but was " + status);
        }
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

    public CampaignRunStatus getStatus() {
        return status;
    }

    public Instant getPreparedAt() {
        return preparedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
