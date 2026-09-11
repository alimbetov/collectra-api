package io.collectra.api.communication.application;

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
    private final Clock clock;

    public MessageStateService(MessageRepository messages, Clock clock) {
        this.messages = messages;
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
    public void markSent(UUID tenantId, UUID messageId, String providerMessageId) {
        locked(tenantId, messageId).markSent(providerMessageId, clock.instant());
    }

    @Transactional
    public void scheduleRetry(
            UUID tenantId,
            UUID messageId,
            Instant nextRetryAt,
            String errorCode,
            String errorMessage) {
        locked(tenantId, messageId).scheduleRetry(nextRetryAt, errorCode, errorMessage);
    }

    @Transactional
    public void markFailed(UUID tenantId, UUID messageId, String errorCode, String errorMessage) {
        locked(tenantId, messageId).markFailed(errorCode, errorMessage);
    }

    private Message locked(UUID tenantId, UUID messageId) {
        return messages.findLockedByIdAndTenantId(tenantId, messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found"));
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
