package io.collectra.api.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 120)
    private String lockedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxEvent() {}

    public OutboxEvent(
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload) {
        this(tenantId, aggregateType, aggregateId, eventType, payload, Instant.now());
    }

    public OutboxEvent(
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.status = OutboxEventStatus.PENDING;
        this.nextAttemptAt = Objects.requireNonNull(now, "now");
        this.createdAt = now;
    }

    public void claim(String workerId, Instant now) {
        if (status != OutboxEventStatus.PENDING && status != OutboxEventStatus.RETRY_WAIT) {
            throw new IllegalStateException("Outbox event is not claimable from status " + status);
        }
        if (nextAttemptAt.isAfter(now)) {
            throw new IllegalStateException("Outbox event is not ready yet");
        }
        status = OutboxEventStatus.PROCESSING;
        attemptCount++;
        lockedAt = Objects.requireNonNull(now, "now");
        lockedBy = requireText(workerId, "workerId");
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void markPublished(Instant now) {
        requireProcessing();
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = Objects.requireNonNull(now, "now");
        nextAttemptAt = now;
        clearLock();
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void scheduleRetry(Instant nextAttemptAt, String code, String message) {
        requireProcessing();
        status = OutboxEventStatus.RETRY_WAIT;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        lastErrorCode = requireText(code, "code");
        lastErrorMessage = truncate(message, 1000);
        clearLock();
    }

    public void markDead(String code, String message) {
        requireProcessing();
        status = OutboxEventStatus.DEAD;
        lastErrorCode = requireText(code, "code");
        lastErrorMessage = truncate(message, 1000);
        clearLock();
    }

    public void recover(Instant nextAttemptAt, String code, String message) {
        requireProcessing();
        status = OutboxEventStatus.RETRY_WAIT;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        lastErrorCode = requireText(code, "code");
        lastErrorMessage = truncate(message, 1000);
        clearLock();
    }

    public boolean ownedBy(String workerId) {
        return status == OutboxEventStatus.PROCESSING && Objects.equals(lockedBy, workerId);
    }

    private void requireProcessing() {
        if (status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Outbox event is not processing: " + status);
        }
    }

    private void clearLock() {
        lockedAt = null;
        lockedBy = null;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
}
