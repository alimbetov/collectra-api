package io.collectra.api.shared.outbox;

import io.collectra.api.communication.application.MessageDeliveryRequested;
import io.collectra.api.communication.infrastructure.messaging.CommunicationMessagingConfig;
import io.collectra.api.document.application.DocumentGenerationEventPublisher;
import io.collectra.api.document.application.GenerationJobService;
import io.collectra.api.document.infrastructure.DocumentMessagingConfig;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventRouter {
    public OutboxRoute route(String eventType) {
        if (GenerationJobService.REQUESTED_EVENT_TYPE.equals(eventType)) {
            return new OutboxRoute(
                    DocumentMessagingConfig.EXCHANGE, DocumentMessagingConfig.ROUTING_KEY);
        }
        if (DocumentGenerationEventPublisher.COMPLETED_EVENT_TYPE.equals(eventType)) {
            return new OutboxRoute(
                    DocumentMessagingConfig.EXCHANGE,
                    DocumentMessagingConfig.COMPLETED_ROUTING_KEY);
        }
        if (DocumentGenerationEventPublisher.FAILED_EVENT_TYPE.equals(eventType)) {
            return new OutboxRoute(
                    DocumentMessagingConfig.EXCHANGE, DocumentMessagingConfig.FAILED_ROUTING_KEY);
        }
        if (MessageDeliveryRequested.EVENT_TYPE.equals(eventType)) {
            return new OutboxRoute(
                    CommunicationMessagingConfig.EXCHANGE,
                    CommunicationMessagingConfig.ROUTING_KEY);
        }
        throw new UnknownOutboxEventTypeException(eventType);
    }
}
