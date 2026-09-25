package io.collectra.api.integration.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.integration.application.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class IngestionListener {
    private final IngestionWorker worker;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public IngestionListener(IngestionWorker worker, RabbitTemplate rabbit, ObjectMapper json) {
        this.worker = worker;
        this.rabbit = rabbit;
        this.json = json;
    }

    @RabbitListener(queues = IngestionMessagingConfig.QUEUE)
    public void consume(Message message) {
        JsonNode payload = readPayload(message);
        try {
            UUID tenantId = required(payload, "tenantId");
            UUID ingestionId = required(payload, "ingestionId");
            worker.process(tenantId, ingestionId);
        } catch (IngestionRetryableException ex) {
            int retry = header(message, "x-retry-count");
            if (retry >= 3) {
                dead(payload, retry);
                return;
            }
            String delay = retry == 0 ? "1m" : retry == 1 ? "10m" : "1h";
            rabbit.convertAndSend(
                    IngestionMessagingConfig.RETRY_EXCHANGE,
                    delay,
                    payload,
                    m -> {
                        m.getMessageProperties().setHeader("x-retry-count", retry + 1);
                        return m;
                    });
        } catch (RuntimeException poison) {
            dead(payload, header(message, "x-retry-count"));
        }
    }

    private JsonNode readPayload(Message message) {
        try {
            return json.readTree(message.getBody());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid ingestion message payload", ex);
        }
    }

    private void dead(JsonNode payload, int retry) {
        rabbit.convertAndSend(
                IngestionMessagingConfig.EXCHANGE,
                "integration.ingestion.dead",
                payload,
                m -> {
                    m.getMessageProperties().setHeader("x-retry-count", retry);
                    return m;
                });
    }

    private int header(Message m, String name) {
        Object v = m.getMessageProperties().getHeader(name);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private UUID required(JsonNode p, String name) {
        String v = p.path(name).asText(null);
        if (v == null || v.isBlank()) throw new IllegalArgumentException(name + " is required");
        return UUID.fromString(v);
    }
}
