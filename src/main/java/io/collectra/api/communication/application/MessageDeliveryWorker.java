package io.collectra.api.communication.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "collectra.communication.delivery.enabled", havingValue = "true")
public class MessageDeliveryWorker {
    private final MessageStateService states;
    private final MessageAttachmentContentResolver attachmentContent;
    private final DeliveryGateway gateway;
    private final MessageRetryPolicy retryPolicy;
    private final Clock clock;

    public MessageDeliveryWorker(
            MessageStateService states,
            MessageAttachmentContentResolver attachmentContent,
            DeliveryGateway gateway,
            MessageRetryPolicy retryPolicy,
            Clock clock) {
        this.states = states;
        this.attachmentContent = attachmentContent;
        this.gateway = gateway;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    public void deliver(UUID tenantId, UUID messageId) {
        var claimed = states.begin(tenantId, messageId);
        if (claimed.isEmpty()) {
            return;
        }

        MessageDeliverySnapshot message = claimed.get();
        List<DeliveryAttachment> attachments;
        try {
            attachments = attachmentContent.resolve(tenantId, messageId);
        } catch (AttachmentResolutionException failure) {
            handleResolutionFailure(message, failure);
            return;
        }

        DeliveryResult result =
                gateway.deliver(
                        new DeliveryCommand(
                                message.messageId(),
                                message.tenantId(),
                                message.channel(),
                                message.destination(),
                                message.subject(),
                                message.body(),
                                attachments));
        if (result instanceof DeliveryResult.Accepted accepted) {
            states.markSent(tenantId, messageId, accepted.providerMessageId());
            return;
        }

        DeliveryResult.Rejected rejected = (DeliveryResult.Rejected) result;
        if (rejected.kind() == DeliveryFailureKind.RETRYABLE
                && !retryPolicy.exhausted(message.attemptCount())) {
            states.scheduleRetry(
                    tenantId,
                    messageId,
                    retryPolicy.nextRetryAt(message.attemptCount(), clock.instant()),
                    rejected.code(),
                    rejected.message());
            return;
        }
        states.markFailed(tenantId, messageId, rejected.code(), rejected.message());
    }

    private void handleResolutionFailure(
            MessageDeliverySnapshot message, AttachmentResolutionException failure) {
        if (failure.kind() == DeliveryFailureKind.RETRYABLE
                && !retryPolicy.exhausted(message.attemptCount())) {
            states.scheduleRetry(
                    message.tenantId(),
                    message.messageId(),
                    retryPolicy.nextRetryAt(message.attemptCount(), clock.instant()),
                    failure.code(),
                    failure.getMessage());
            return;
        }
        states.markFailed(
                message.tenantId(), message.messageId(), failure.code(), failure.getMessage());
    }
}
