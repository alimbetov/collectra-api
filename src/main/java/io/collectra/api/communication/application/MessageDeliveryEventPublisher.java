package io.collectra.api.communication.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.shared.outbox.OutboxService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageDeliveryEventPublisher {
    private final OutboxService outbox;
    private final ObjectMapper json;

    public MessageDeliveryEventPublisher(OutboxService outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestDelivery(UUID tenantId, UUID messageId) {
        MessageDeliveryRequested event = new MessageDeliveryRequested(tenantId, messageId);
        outbox.append(
                tenantId,
                MessageDeliveryRequested.AGGREGATE_TYPE,
                messageId,
                MessageDeliveryRequested.EVENT_TYPE,
                serialize(event));
    }

    private String serialize(MessageDeliveryRequested event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize message delivery event", ex);
        }
    }
}
