package io.collectra.api.document.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.document.application.DocumentGenerationWorker;
import io.collectra.api.document.application.GenerationJobStateService;
import io.collectra.api.document.application.PdfRenderer;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DocumentGenerationListener {
    private final DocumentGenerationWorker worker;
    private final GenerationJobStateService states;
    private final RabbitTemplate rabbit;

    public DocumentGenerationListener(
            DocumentGenerationWorker worker,
            GenerationJobStateService states,
            RabbitTemplate rabbit) {
        this.worker = worker;
        this.states = states;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = DocumentMessagingConfig.QUEUE)
    public void consume(JsonNode payload, Message message) {
        UUID tenantId = requiredUuid(payload, "tenantId");
        UUID jobId = requiredUuid(payload, "jobId");
        int retry =
                message.getMessageProperties().getHeader("x-retry-count") == null
                        ? 0
                        : ((Number) message.getMessageProperties().getHeader("x-retry-count"))
                                .intValue();
        try {
            worker.generate(tenantId, jobId);
        } catch (Exception ex) {
            if (permanent(ex) || retry >= 3) {
                states.fail(tenantId, jobId, "GENERATION_FAILED", rootMessage(ex));
                rabbit.convertAndSend(
                        DocumentMessagingConfig.EXCHANGE,
                        "generation.dead",
                        payload,
                        outgoing -> {
                            outgoing.getMessageProperties().setHeader("x-retry-count", retry);
                            return outgoing;
                        });
                return;
            }
            states.retry(tenantId, jobId, "GENERATION_RETRY", rootMessage(ex));
            String delay = retry == 0 ? "1m" : retry == 1 ? "10m" : "1h";
            rabbit.convertAndSend(
                    DocumentMessagingConfig.RETRY_EXCHANGE,
                    delay,
                    payload,
                    outgoing -> {
                        outgoing.getMessageProperties().setHeader("x-retry-count", retry + 1);
                        return outgoing;
                    });
        }
    }

    private UUID requiredUuid(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return UUID.fromString(value);
    }

    private boolean permanent(Exception ex) {
        return ex instanceof IllegalArgumentException
                || ex instanceof PdfRenderer.PdfRenderingException
                || ex instanceof java.util.NoSuchElementException;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
