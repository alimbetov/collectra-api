package io.collectra.api.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.shared.outbox.OutboxService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentGenerationRequestPublisher {
    public static final String EVENT_TYPE = "DOCUMENT_GENERATION_REQUESTED";

    private final OutboxService outbox;
    private final ObjectMapper json;

    public DocumentGenerationRequestPublisher(OutboxService outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requested(UUID tenantId, UUID jobId) {
        outbox.append(
                tenantId,
                "GENERATION_JOB",
                jobId,
                EVENT_TYPE,
                serialize(Map.of("tenantId", tenantId, "jobId", jobId)));
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize generation request", ex);
        }
    }
}
