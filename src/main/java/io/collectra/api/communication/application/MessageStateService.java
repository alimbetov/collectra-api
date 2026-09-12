package io.collectra.api.communication.application;

import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageStateService {
    private final MessageRepository messages;
    private final CampaignRunRepository runs;
    private final MessageRetryPolicy retryPolicy;
    private final Clock clock;

    public MessageStateService(
            MessageRepository messages,
            CampaignRunRepository runs,
            MessageRetryPolicy retryPolicy,
            Clock clock) {
        this.messages = messages;
        this.runs = runs;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Transactional
    public Optional<MessageDeliverySnapshot> begin(UUID tenantId, UUID messageId) {
        Message message = locked(tenantId, messageId);
        if (message.getStatus() != MessageStatus.QUEUED) {
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
        message.markSent(providerMessageId, clock.instant());
        run.messageSent();
        run.completeIfTerminal(clock.instant());
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
        message.markFailed(errorCode, errorMessage);
        run.messageFailed();
        run.completeIfTerminal(clock.instant());
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
        } else {
            message.scheduleRetry(
                    retryPolicy.nextRetryAt(message.getAttemptCount(), recoveryTime),
                    MessageRecoveryService.PROCESSING_TIMEOUT,
                    MessageRecoveryService.PROCESSING_TIMEOUT_MESSAGE);
            run.messageRetryScheduled();
        }
        return true;
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
