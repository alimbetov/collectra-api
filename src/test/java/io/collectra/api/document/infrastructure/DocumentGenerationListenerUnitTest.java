package io.collectra.api.document.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.document.application.DocumentGenerationWorker;
import io.collectra.api.document.application.GenerationJobStateService;
import io.collectra.api.document.infrastructure.MinioDocumentStorage.DocumentStorageException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class DocumentGenerationListenerUnitTest {
    private final DocumentGenerationWorker worker = mock(DocumentGenerationWorker.class);
    private final GenerationJobStateService states = mock(GenerationJobStateService.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final DocumentGenerationListener listener =
            new DocumentGenerationListener(worker, states, rabbit);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void routesTransientFailureThroughDelayedRetryQueue() {
        UUID tenantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var payload =
                json.createObjectNode()
                        .put("tenantId", tenantId.toString())
                        .put("jobId", jobId.toString());
        doThrow(new DocumentStorageException("storage unavailable", new java.io.IOException()))
                .when(worker)
                .generate(tenantId, jobId);

        listener.consume(payload, new Message(new byte[0], new MessageProperties()));

        verify(states).retry(tenantId, jobId, "GENERATION_RETRY", "IOException");
        verify(rabbit)
                .convertAndSend(
                        eq(DocumentMessagingConfig.RETRY_EXCHANGE),
                        eq("1m"),
                        eq(payload),
                        any(MessagePostProcessor.class));
    }

    @Test
    void routesPermanentFailureToDeadLetterQueue() {
        UUID tenantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var payload =
                json.createObjectNode()
                        .put("tenantId", tenantId.toString())
                        .put("jobId", jobId.toString());
        doThrow(new IllegalArgumentException("invalid template"))
                .when(worker)
                .generate(tenantId, jobId);

        listener.consume(payload, new Message(new byte[0], new MessageProperties()));

        verify(states).fail(tenantId, jobId, "GENERATION_FAILED", "invalid template");
        verify(rabbit)
                .convertAndSend(
                        eq(DocumentMessagingConfig.EXCHANGE),
                        eq("generation.dead"),
                        eq(payload),
                        any(MessagePostProcessor.class));
    }
}
