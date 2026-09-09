package io.collectra.api.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.collectra.api.document.infrastructure.DocumentMessagingConfig;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "collectra.messaging.outbox-enabled", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxRepository events;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public OutboxPublisher(OutboxRepository events, RabbitTemplate rabbit, ObjectMapper json) {
        this.events = events;
        this.rabbit = rabbit;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${collectra.messaging.outbox-publish-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event :
                events.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                        "PENDING", Instant.now())) {
            try {
                if (!"DOCUMENT_GENERATION_REQUESTED".equals(event.getEventType())) {
                    event.published();
                    continue;
                }
                rabbit.convertAndSend(
                        DocumentMessagingConfig.EXCHANGE,
                        DocumentMessagingConfig.ROUTING_KEY,
                        json.readTree(event.getPayload()));
                event.published();
            } catch (Exception ex) {
                event.failed();
            }
        }
    }
}
