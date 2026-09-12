package io.collectra.api.communication.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
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
        name = "messages",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_messages_campaign_recipient",
                        columnNames = "campaign_recipient_id"))
public class Message extends AuditableEntity {
    private static final int DESTINATION_MAX_LENGTH = 500;
    private static final int LOCALE_MAX_LENGTH = 35;
    private static final int SUBJECT_MAX_LENGTH = 500;
    private static final int DELIVERY_KEY_MAX_LENGTH = 100;
    private static final int PROVIDER_MESSAGE_ID_MAX_LENGTH = 255;
    private static final int ERROR_CODE_MAX_LENGTH = 80;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "campaign_run_id", nullable = false)
    private UUID campaignRunId;

    @Column(name = "campaign_recipient_id", nullable = false)
    private UUID campaignRecipientId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommunicationChannel channel;

    @Column(nullable = false, length = DESTINATION_MAX_LENGTH)
    private String destination;

    @Column(name = "resolved_locale", nullable = false, length = LOCALE_MAX_LENGTH)
    private String resolvedLocale;

    @Column(length = SUBJECT_MAX_LENGTH)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    @Column(
            name = "delivery_key",
            nullable = false,
            length = DELIVERY_KEY_MAX_LENGTH,
            unique = true)
    private String deliveryKey;

    @Column(name = "delivery_requested_at")
    private Instant deliveryRequestedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "processing_attempt_count", nullable = false)
    private int processingAttemptCount;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "provider_message_id", length = PROVIDER_MESSAGE_ID_MAX_LENGTH)
    private String providerMessageId;

    @Column(name = "last_error_code", length = ERROR_CODE_MAX_LENGTH)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = ERROR_MESSAGE_MAX_LENGTH)
    private String lastErrorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Message() {}

    private Message(
            UUID tenantId,
            UUID campaignId,
            UUID campaignRunId,
            UUID campaignRecipientId,
            UUID customerId,
            UUID invoiceId,
            UUID templateVersionId,
            CommunicationChannel channel,
            String destination,
            String resolvedLocale,
            String subject,
            String body) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.campaignId = Objects.requireNonNull(campaignId, "campaignId is required");
        this.campaignRunId = Objects.requireNonNull(campaignRunId, "campaignRunId is required");
        this.campaignRecipientId =
                Objects.requireNonNull(campaignRecipientId, "campaignRecipientId is required");
        this.customerId = Objects.requireNonNull(customerId, "customerId is required");
        this.invoiceId = invoiceId;
        this.templateVersionId =
                Objects.requireNonNull(templateVersionId, "templateVersionId is required");
        this.channel = Objects.requireNonNull(channel, "channel is required");
        this.destination = required(destination, "destination", DESTINATION_MAX_LENGTH);
        this.resolvedLocale = required(resolvedLocale, "resolvedLocale", LOCALE_MAX_LENGTH);
        this.subject = optional(subject, "subject", SUBJECT_MAX_LENGTH);
        if (channel == CommunicationChannel.EMAIL && this.subject == null) {
            throw new IllegalArgumentException("subject is required for EMAIL");
        }
        this.body = required(body, "body");
        this.deliveryKey = "msg-" + id;
        this.status = MessageStatus.QUEUED;
    }

    public static Message queued(
            UUID tenantId,
            UUID campaignId,
            UUID campaignRunId,
            UUID campaignRecipientId,
            UUID customerId,
            UUID invoiceId,
            UUID templateVersionId,
            CommunicationChannel channel,
            String destination,
            String resolvedLocale,
            String subject,
            String body) {
        return new Message(
                tenantId,
                campaignId,
                campaignRunId,
                campaignRecipientId,
                customerId,
                invoiceId,
                templateVersionId,
                channel,
                destination,
                resolvedLocale,
                subject,
                body);
    }

    public boolean markDeliveryRequested(Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (deliveryRequestedAt != null) {
            return false;
        }
        requireStatus(MessageStatus.QUEUED);
        deliveryRequestedAt = now;
        return true;
    }

    public void beginAttempt(Instant now) {
        requireStatus(MessageStatus.QUEUED);
        status = MessageStatus.PROCESSING;
        processingStartedAt = Objects.requireNonNull(now, "now is required");
        processingAttemptCount++;
    }

    public int beginProviderAttempt() {
        requireStatus(MessageStatus.PROCESSING);
        attemptCount++;
        return attemptCount;
    }

    public void markSent(String providerMessageId, Instant now) {
        requireStatus(MessageStatus.PROCESSING);
        completeAsSent(providerMessageId, now);
    }

    public void resolveUnknownAsSent(String providerMessageId, Instant now) {
        requireStatus(MessageStatus.UNKNOWN);
        completeAsSent(providerMessageId, now);
    }

    private void completeAsSent(String providerMessageId, Instant now) {
        Instant acceptedAt = Objects.requireNonNull(now, "now is required");
        String normalizedProviderMessageId =
                optional(providerMessageId, "providerMessageId", PROVIDER_MESSAGE_ID_MAX_LENGTH);
        status = MessageStatus.SENT;
        this.providerMessageId = normalizedProviderMessageId;
        sentAt = acceptedAt;
        processingStartedAt = null;
        nextRetryAt = null;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void markUnknown(String errorCode, String errorMessage) {
        requireStatus(MessageStatus.PROCESSING);
        status = MessageStatus.UNKNOWN;
        processingStartedAt = null;
        nextRetryAt = null;
        lastErrorCode = normalizedErrorCode(errorCode);
        lastErrorMessage = limit(trim(errorMessage), ERROR_MESSAGE_MAX_LENGTH);
    }

    public void scheduleRetry(Instant nextRetryAt, String errorCode, String errorMessage) {
        requireStatus(MessageStatus.PROCESSING);
        Instant retryAt = Objects.requireNonNull(nextRetryAt, "nextRetryAt is required");
        if (!retryAt.isAfter(processingStartedAt)) {
            throw new IllegalArgumentException("nextRetryAt must be after processingStartedAt");
        }
        String normalizedErrorCode = normalizedErrorCode(errorCode);
        String normalizedErrorMessage = limit(trim(errorMessage), ERROR_MESSAGE_MAX_LENGTH);
        status = MessageStatus.RETRY_WAIT;
        processingStartedAt = null;
        this.nextRetryAt = retryAt;
        lastErrorCode = normalizedErrorCode;
        lastErrorMessage = normalizedErrorMessage;
    }

    public void markFailed(String errorCode, String errorMessage) {
        requireStatus(MessageStatus.PROCESSING);
        fail(errorCode, errorMessage);
    }

    public void markFailedBeforeDelivery(String errorCode, String errorMessage) {
        requireStatus(MessageStatus.QUEUED);
        fail(errorCode, errorMessage);
    }

    private void fail(String errorCode, String errorMessage) {
        String normalizedErrorCode = normalizedErrorCode(errorCode);
        String normalizedErrorMessage = limit(trim(errorMessage), ERROR_MESSAGE_MAX_LENGTH);
        status = MessageStatus.FAILED;
        processingStartedAt = null;
        nextRetryAt = null;
        lastErrorCode = normalizedErrorCode;
        lastErrorMessage = normalizedErrorMessage;
    }

    public void requeue() {
        requireStatus(MessageStatus.RETRY_WAIT);
        status = MessageStatus.QUEUED;
        nextRetryAt = null;
    }

    private static String normalizedErrorCode(String errorCode) {
        return limit(required(errorCode, "errorCode"), ERROR_CODE_MAX_LENGTH);
    }

    private void requireStatus(MessageStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected " + expected + " but was " + status);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = required(value, field);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String value, String field, int maxLength) {
        String normalized = trim(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
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

    public UUID getCampaignRunId() {
        return campaignRunId;
    }

    public UUID getCampaignRecipientId() {
        return campaignRecipientId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public CommunicationChannel getChannel() {
        return channel;
    }

    public String getDestination() {
        return destination;
    }

    public String getResolvedLocale() {
        return resolvedLocale;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public String getDeliveryKey() {
        return deliveryKey;
    }

    public Instant getDeliveryRequestedAt() {
        return deliveryRequestedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getProcessingAttemptCount() {
        return processingAttemptCount;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
