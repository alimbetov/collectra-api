package io.collectra.api.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "message_delivery_attempts",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_message_delivery_attempt_number",
                        columnNames = {"message_id", "attempt_no"}))
public class MessageDeliveryAttempt {
    private static final int DELIVERY_KEY_MAX_LENGTH = 100;
    private static final int PROVIDER_REFERENCE_MAX_LENGTH = 255;
    private static final int ERROR_CODE_MAX_LENGTH = 80;

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "delivery_key", nullable = false, length = DELIVERY_KEY_MAX_LENGTH)
    private String deliveryKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryAttemptStatus status;

    @Column(name = "provider_reference", length = PROVIDER_REFERENCE_MAX_LENGTH)
    private String providerReference;

    @Column(name = "error_code", length = ERROR_CODE_MAX_LENGTH)
    private String errorCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected MessageDeliveryAttempt() {}

    private MessageDeliveryAttempt(
            UUID tenantId,
            UUID messageId,
            int attemptNo,
            String deliveryKey,
            Instant startedAt) {
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.messageId = Objects.requireNonNull(messageId, "messageId is required");
        this.attemptNo = attemptNo;
        this.deliveryKey = required(deliveryKey, "deliveryKey", DELIVERY_KEY_MAX_LENGTH);
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt is required");
        this.status = DeliveryAttemptStatus.STARTED;
    }

    public static MessageDeliveryAttempt started(
            UUID tenantId,
            UUID messageId,
            int attemptNo,
            String deliveryKey,
            Instant startedAt) {
        return new MessageDeliveryAttempt(tenantId, messageId, attemptNo, deliveryKey, startedAt);
    }

    public void accepted(String providerReference, Instant completedAt) {
        complete(DeliveryAttemptStatus.ACCEPTED, providerReference, null, completedAt);
    }

    public void retryableFailure(String errorCode, Instant completedAt) {
        complete(DeliveryAttemptStatus.RETRYABLE_FAILURE, null, errorCode, completedAt);
    }

    public void permanentFailure(String errorCode, Instant completedAt) {
        complete(DeliveryAttemptStatus.PERMANENT_FAILURE, null, errorCode, completedAt);
    }

    public void unknown(String errorCode, Instant completedAt) {
        complete(DeliveryAttemptStatus.UNKNOWN, null, errorCode, completedAt);
    }

    private void complete(
            DeliveryAttemptStatus result,
            String providerReference,
            String errorCode,
            Instant completedAt) {
        if (status != DeliveryAttemptStatus.STARTED) {
            throw new IllegalStateException("Delivery attempt is already completed");
        }
        this.status = Objects.requireNonNull(result, "result is required");
        this.providerReference = optional(providerReference, PROVIDER_REFERENCE_MAX_LENGTH);
        this.errorCode = optional(errorCode, ERROR_CODE_MAX_LENGTH);
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt is required");
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getMessageId() { return messageId; }
    public int getAttemptNo() { return attemptNo; }
    public String getDeliveryKey() { return deliveryKey; }
    public DeliveryAttemptStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public String getErrorCode() { return errorCode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
