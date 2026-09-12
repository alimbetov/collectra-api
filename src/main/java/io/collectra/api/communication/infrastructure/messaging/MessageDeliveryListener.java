package io.collectra.api.communication.infrastructure.messaging;

import io.collectra.api.communication.application.MessageDeliveryRequested;
import io.collectra.api.communication.application.MessageDeliveryWorker;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "collectra.communication.delivery.enabled", havingValue = "true")
public class MessageDeliveryListener {
    private final MessageDeliveryWorker worker;

    public MessageDeliveryListener(MessageDeliveryWorker worker) {
        this.worker = worker;
    }

    @RabbitListener(queues = CommunicationMessagingConfig.MESSAGE_DELIVERY_QUEUE)
    public void consume(MessageDeliveryRequested event) {
        worker.deliver(event.tenantId(), event.messageId());
    }
}
