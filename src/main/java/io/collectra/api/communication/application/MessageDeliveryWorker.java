package io.collectra.api.communication.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final DeliveryFaultInjector faultInjector;

    public MessageDeliveryWorker(
            MessageStateService states,
            MessageAttachmentContentResolver attachmentContent,
            DeliveryGateway gateway,
            MessageRetryPolicy retryPolicy,
            Clock clock) {
        this(states, attachmentContent, gateway, retryPolicy, clock, DeliveryFaultInjector.noop());
    }

    @Autowired
    public MessageDeliveryWorker(
            MessageStateService states,
            MessageAttachmentContentResolver attachmentContent,
            DeliveryGateway gateway,
            MessageRetryPolicy retryPolicy,
            Clock clock,
            DeliveryFaultInjector faultInjector) {
        this.states = states;
        this.attachmentContent = attachmentContent;
        this.gateway = gateway;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.faultInjector = faultInjector;
    }

    public void deliver(UUID tenantId, UUID messageId) {
        var claimed = states.begin(tenantId, messageId);
        if (claimed.isEmpty()) {
            return;
        }
        faultInjector.hit(DeliveryFaultPoint.AFTER_CLAIM);

        MessageDeliverySnapshot message = claimed.get();
        List<DeliveryAttachment> attachments;
        try {
            attachments = attachmentContent.resolve(tenantId, messageId);
        } catch (AttachmentResolutionException failure) {
            handleResolutionFailure(message, failure);
            return;
        }

        faultInjector.hit(DeliveryFaultPoint.BEFORE_PROVIDER);
        ProviderAttemptSnapshot attempt =
                states.beginProviderAttempt(tenantId, messageId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Message lost PROCESSING ownership before provider call"));

        DeliveryResult result =
                gateway.deliver(
                        new DeliveryCommand(
                                message.messageId(),
                                message.tenantId(),
                                attempt.deliveryKey(),
                                attempt.attemptNo(),
                                message.channel(),
                                message.destination(),
                                message.subject(),
                                message.body(),
                                attachments));
        if (result instanceof DeliveryResult.Accepted accepted) {
            faultInjector.hit(DeliveryFaultPoint.AFTER_PROVIDER_ACCEPTED);
            faultInjector.hit(DeliveryFaultPoint.BEFORE_STATE_COMMIT);
            states.markSent(tenantId, messageId, accepted.providerMessageId());
            faultInjector.hit(DeliveryFaultPoint.AFTER_STATE_COMMIT);
            return;
        }
        if (result instanceof DeliveryResult.Unknown unknown) {
            faultInjector.hit(DeliveryFaultPoint.BEFORE_STATE_COMMIT);
            states.markUnknown(tenantId, messageId, unknown.code(), unknown.message());
            faultInjector.hit(DeliveryFaultPoint.AFTER_STATE_COMMIT);
            return;
        }

        DeliveryResult.Rejected rejected = (DeliveryResult.Rejected) result;
        faultInjector.hit(DeliveryFaultPoint.BEFORE_STATE_COMMIT);
        if (rejected.kind() == DeliveryFailureKind.RETRYABLE
                && !retryPolicy.exhausted(attempt.attemptNo())) {
            states.scheduleRetry(
                    tenantId,
                    messageId,
                    retryPolicy.nextRetryAt(attempt.attemptNo(), clock.instant()),
                    rejected.code(),
                    rejected.message());
            faultInjector.hit(DeliveryFaultPoint.AFTER_STATE_COMMIT);
            return;
        }
        states.markFailed(tenantId, messageId, rejected.code(), rejected.message());
        faultInjector.hit(DeliveryFaultPoint.AFTER_STATE_COMMIT);
    }

    private void handleResolutionFailure(
            MessageDeliverySnapshot message, AttachmentResolutionException failure) {
        int retryOrdinal = Math.max(1, message.processingAttemptCount());
        if (failure.kind() == DeliveryFailureKind.RETRYABLE
                && !retryPolicy.exhausted(retryOrdinal)) {
            states.scheduleRetry(
                    message.tenantId(),
                    message.messageId(),
                    retryPolicy.nextRetryAt(retryOrdinal, clock.instant()),
                    failure.code(),
                    failure.getMessage());
            return;
        }
        states.markFailed(
                message.tenantId(), message.messageId(), failure.code(), failure.getMessage());
    }
}
