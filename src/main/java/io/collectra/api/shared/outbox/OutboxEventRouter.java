package io.collectra.api.shared.outbox;

import io.collectra.api.document.infrastructure.DocumentMessagingConfig;

import org.springframework.stereotype.Component;

@Component
public class OutboxEventRouter {
    public OutboxRoute route(String eventType) {
        if ("DOCUMENT_GENERATION_REQUESTED".equals(eventType)) {
            return new OutboxRoute(
                    DocumentMessagingConfig.EXCHANGE, DocumentMessagingConfig.ROUTING_KEY);
        }
        throw new UnknownOutboxEventTypeException(eventType);
    }
}
