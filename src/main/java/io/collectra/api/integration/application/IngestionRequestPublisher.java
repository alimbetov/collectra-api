package io.collectra.api.integration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.shared.outbox.OutboxService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionRequestPublisher {
    private final OutboxService outbox;
    private final ObjectMapper json;

    public IngestionRequestPublisher(OutboxService outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requested(UUID tenantId, UUID ingestionId) {
        outbox.append(
                tenantId,
                "INGESTION",
                ingestionId,
                IngestionApplicationService.EVENT_TYPE,
                serialize(Map.of("tenantId", tenantId, "ingestionId", ingestionId)));
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize ingestion request", ex);
        }
    }
}
