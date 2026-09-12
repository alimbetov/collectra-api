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

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    protected CampaignRun() {}

    public CampaignRun(UUID tenantId, UUID campaignId) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.campaignId = Objects.requireNonNull(campaignId);
        this.status = CampaignRunStatus.PREPARING;
    }

    public void ready(int recipientCount, Instant now) {
        require(CampaignRunStatus.PREPARING);
        if (recipientCount < 0) {
            throw new IllegalArgumentException("recipientCount must not be negative");
        }
        status = CampaignRunStatus.READY;
        this.recipientCount = recipientCount;
        preparedAt = Objects.requireNonNull(now, "now is required");
    }

    public void start(Instant now) {
        require(CampaignRunStatus.READY);
        status = CampaignRunStatus.RUNNING;
        startedAt = Objects.requireNonNull(now, "now is required");
    }

    public void messageSent() {
        requireMutableRunning();
        ensureTerminalCapacity(1);
        sentCount++;
    }

    public void messageFailed() {
        requireMutableRunning();
        ensureTerminalCapacity(1);
        failedCount++;
    }

    public void messageRetryScheduled() {
        requireMutableRunning();
        retryCount++;
    }

    public void recipientSkipped() {
        recipientsSkipped(1);
    }

    public void recipientsSkipped(int count) {
        requireMutableRunning();
        if (count < 0) {
            throw new IllegalArgumentException("Skipped count increment must not be negative");
        }
        ensureTerminalCapacity(count);
        skippedCount += count;
    }

    public int terminalCount() {
        return sentCount + failedCount + skippedCount;
    }

    public boolean completeIfTerminal(Instant now) {
        requireMutableRunning();
        if (terminalCount() < recipientCount) {
            return false;
        }
        complete(now);
        return true;
    }

    public void complete(Instant now) {
        require(CampaignRunStatus.RUNNING);
        if (terminalCount() != recipientCount) {
            throw new IllegalStateException("Campaign run still has non-terminal recipients");
        }
        status = CampaignRunStatus.COMPLETED;
        completedAt = Objects.requireNonNull(now, "now is required");
    }

    public void fail(Instant now) {
        if (status != CampaignRunStatus.RUNNING && status != CampaignRunStatus.PREPARING) {
            throw new IllegalStateException("Campaign run cannot fail from " + status);
        }
        status = CampaignRunStatus.FAILED;
        completedAt = Objects.requireNonNull(now, "now is required");
    }

    public void cancel(Instant now) {
        if (status == CampaignRunStatus.COMPLETED
                || status == CampaignRunStatus.FAILED
                || status == CampaignRunStatus.CANCELLED) {
            throw new IllegalStateException("Finished campaign run cannot be cancelled");
        }
        status = CampaignRunStatus.CANCELLED;
        completedAt = Objects.requireNonNull(now, "now is required");
    }

    private void ensureTerminalCapacity(int increment) {
        if (terminalCount() + increment > recipientCount) {
            throw new IllegalStateException("Campaign run terminal counters exceed recipientCount");
        }
    }

    private void requireMutableRunning() {
        require(CampaignRunStatus.RUNNING);
        if (sentCount < 0 || failedCount < 0 || skippedCount < 0 || retryCount < 0) {
            throw new IllegalStateException("Campaign run counters must not be negative");
        }
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

    public int getRecipientCount() {
        return recipientCount;
    }

    public int getSentCount() {
        return sentCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
