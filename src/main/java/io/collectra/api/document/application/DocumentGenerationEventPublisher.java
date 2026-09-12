package io.collectra.api.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.shared.outbox.OutboxService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentGenerationEventPublisher {
    public static final String AGGREGATE_TYPE = "GENERATION_JOB";
    public static final String COMPLETED_EVENT_TYPE = "DOCUMENT_GENERATION_COMPLETED";
    public static final String FAILED_EVENT_TYPE = "DOCUMENT_GENERATION_FAILED";

    private final OutboxService outbox;
    private final ObjectMapper json;

    public DocumentGenerationEventPublisher(OutboxService outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completed(UUID tenantId, UUID jobId) {
        outbox.append(
                tenantId,
                AGGREGATE_TYPE,
                jobId,
                COMPLETED_EVENT_TYPE,
                serialize(new Completed(tenantId, jobId)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void failed(UUID tenantId, UUID jobId, String errorCode) {
        outbox.append(
                tenantId,
                AGGREGATE_TYPE,
                jobId,
                FAILED_EVENT_TYPE,
                serialize(new Failed(tenantId, jobId, errorCode)));
    }

    private String serialize(Object event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize document generation event", ex);
        }
    }

    public record Completed(UUID tenantId, UUID jobId) {}

    public record Failed(UUID tenantId, UUID jobId, String errorCode) {}
}
