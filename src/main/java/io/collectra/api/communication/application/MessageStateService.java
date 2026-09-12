package io.collectra.api.communication.application;

import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.communication.observability.DeliveryOutcomeEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageStateService {
    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final CampaignRunRepository runs;
    private final MessageRetryPolicy retryPolicy;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public MessageStateService(
            MessageRepository messages,
            MessageAttachmentRepository attachments,
            CampaignRunRepository runs,
            MessageRetryPolicy retryPolicy,
            Clock clock,
            ApplicationEventPublisher events) {
        this.messages = messages;
        this.attachments = attachments;
        this.runs = runs;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public Optional<MessageDeliverySnapshot> begin(UUID tenantId, UUID messageId) {
        Message message = locked(tenantId, messageId);
        if (message.getStatus() != MessageStatus.QUEUED) {
            return Optional.empty();
        }
        if (attachments.existsRequiredNotReady(
                tenantId, messageId, MessageAttachmentStatus.READY)) {
            return Optional.empty();
        }
        message.beginAttempt(clock.instant());
        return Optional.of(snapshot(message));
    }

    @Transactional
    public boolean markSent(UUID tenantId, UUID messageId, String providerMessageId) {
        Message message = locked(tenantId, messageId);
        if (message.getStatus() != MessageStatus.PROCESSING) {
            return false;
        }
        CampaignRun run = lockedRun(message);
        Instant now = clock.instant();
        message.markSent(providerMessageId, now);
        run.messageSent();
        run.completeIfTerminal(now);
        publish(message, DeliveryOutcomeEvent.Outcome.SENT, null, now);
        return true;
    }

    @Transactional
    public boolean scheduleRetry(
            UUID tenantId,
            UUID messageId,
            Instant nextRetryAt,
            String errorCode,
            String errorMessage) {
        Message message = locked(tenantId, messageId);
        if (message.getStatus() != MessageStatus.PROCESSING) {
            return false;
        }
        CampaignRun run = lockedRun(message);
        message.scheduleRetry(nextRetryAt, errorCode, errorMessage);
        run.messageRetryScheduled();
        publish(
                message,
                DeliveryOutcomeEvent.Outcome.RETRY_SCHEDULED,
                errorCode,
                clock.instant());
        return true;
    }

    @Transactional
    public boolean markFailed(
            UUID tenantId, UUID messageId, String errorCode, String errorMessage) {
        Message message = locked(tenantId, messageId);
        if (message.getStatus() != MessageStatus.PROCESSING) {
            return false;
        }
        CampaignRun run = lockedRun(message);
        Instant now = clock.instant();
        message.markFailed(errorCode, errorMessage);
        run.messageFailed();
        run.completeIfTerminal(now);
        publish(message, DeliveryOutcomeEvent.Outcome.FAILED, errorCode, now);
        return true;
    }

    @Transactional
    public boolean failBeforeDelivery(
            UUID tenantId, UUID messageId, String errorCode, String errorMessage) {
        Message message = locked(tenantId, messageId);
        if (message.getStatus() != MessageStatus.QUEUED) {
            return false;
        }
        CampaignRun run = lockedRun(message);
        Instant now = clock.instant();
        message.markFailedBeforeDelivery(errorCode, errorMessage);
        run.messageFailed();
        run.completeIfTerminal(now);
        publish(message, DeliveryOutcomeEvent.Outcome.FAILED, errorCode, now);
        return true;
    }

    @Transactional
    public boolean recoverStale(
            UUID tenantId, UUID messageId, Instant cutoff, Instant recoveryTime) {
        Message message = locked(tenantId, messageId);
        if (message.getStatus() != MessageStatus.PROCESSING
                || message.getProcessingStartedAt() == null
                || !message.getProcessingStartedAt().isBefore(cutoff)) {
            return false;
        }

        CampaignRun run = lockedRun(message);
        if (retryPolicy.exhausted(message.getAttemptCount())) {
            message.markFailed(
                    MessageRecoveryService.PROCESSING_TIMEOUT,
                    MessageRecoveryService.PROCESSING_TIMEOUT_MESSAGE);
            run.messageFailed();
            run.completeIfTerminal(recoveryTime);
            publish(
                    message,
                    DeliveryOutcomeEvent.Outcome.FAILED,
                    MessageRecoveryService.PROCESSING_TIMEOUT,
                    recoveryTime);
        } else {
            message.scheduleRetry(
                    retryPolicy.nextRetryAt(message.getAttemptCount(), recoveryTime),
                    MessageRecoveryService.PROCESSING_TIMEOUT,
                    MessageRecoveryService.PROCESSING_TIMEOUT_MESSAGE);
            run.messageRetryScheduled();
            publish(
                    message,
                    DeliveryOutcomeEvent.Outcome.RETRY_SCHEDULED,
                    MessageRecoveryService.PROCESSING_TIMEOUT,
                    recoveryTime);
        }
        return true;
    }

    private void publish(
            Message message,
            DeliveryOutcomeEvent.Outcome outcome,
            String errorCode,
            Instant outcomeAt) {
        events.publishEvent(
                new DeliveryOutcomeEvent(
                        message.getTenantId(),
                        message.getCampaignId(),
                        message.getCampaignRunId(),
                        message.getId(),
                        message.getChannel(),
                        message.getAttemptCount(),
                        outcome,
                        errorCode,
                        message.getCreatedAt(),
                        outcomeAt));
    }

    private Message locked(UUID tenantId, UUID messageId) {
        return messages.findLockedByIdAndTenantId(tenantId, messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found"));
    }

    private CampaignRun lockedRun(Message message) {
        return runs.findLockedByIdAndTenantId(message.getCampaignRunId(), message.getTenantId())
                .orElseThrow(() -> new NoSuchElementException("Campaign run not found"));
    }

    private MessageDeliverySnapshot snapshot(Message message) {
        return new MessageDeliverySnapshot(
                message.getId(),
                message.getTenantId(),
                message.getCampaignRunId(),
                message.getChannel(),
                message.getDestination(),
                message.getSubject(),
                message.getBody(),
                message.getAttemptCount());
    }
}
