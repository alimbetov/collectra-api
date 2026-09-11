package io.collectra.api.communication.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "collectra.communication.delivery.enabled", havingValue = "true")
public class MessageDeliveryWorker {
    private final MessageStateService states;
    private final DeliveryGateway gateway;
    private final MessageRetryPolicy retryPolicy;
    private final Clock clock;

    public MessageDeliveryWorker(
            MessageStateService states,
            DeliveryGateway gateway,
            MessageRetryPolicy retryPolicy,
            Clock clock) {
        this.states = states;
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
        DeliveryResult result =
                gateway.deliver(
                        new DeliveryCommand(
                                message.messageId(),
                                message.tenantId(),
                                message.channel(),
                                message.destination(),
                                message.subject(),
                                message.body()));
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
}
