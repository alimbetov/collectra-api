package io.collectra.api.communication.application;

import io.collectra.api.communication.infrastructure.MessageRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "collectra.communication.delivery.enabled", havingValue = "true")
public class MessageRetryDispatcher {
    private final MessageRepository messages;
    private final MessageDeliveryEventPublisher deliveryEvents;
    private final Clock clock;
    private final int batchSize;

    public MessageRetryDispatcher(
            MessageRepository messages,
            MessageDeliveryEventPublisher deliveryEvents,
            Clock clock,
            @Value("${collectra.communication.retry-dispatch-batch-size:100}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("retryDispatchBatchSize must be positive");
        }
        this.messages = messages;
        this.deliveryEvents = deliveryEvents;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${collectra.communication.retry-dispatch-delay:1m}")
    @Transactional
    public int dispatchDue() {
        var due = messages.findDueRetryForUpdate(clock.instant(), batchSize);
        due.forEach(
                message -> {
                    message.requeue();
                    deliveryEvents.requestDelivery(message.getTenantId(), message.getId());
                });
        return due.size();
    }
}
