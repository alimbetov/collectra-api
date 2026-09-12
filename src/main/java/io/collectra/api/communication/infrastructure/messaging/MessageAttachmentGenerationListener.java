package io.collectra.api.communication.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.communication.application.MessageAttachmentService;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MessageAttachmentGenerationListener {
    private final MessageAttachmentService attachments;

    public MessageAttachmentGenerationListener(MessageAttachmentService attachments) {
        this.attachments = attachments;
    }

    @RabbitListener(queues = CommunicationMessagingConfig.DOCUMENT_COMPLETED_QUEUE)
    public void completed(JsonNode payload) {
        attachments.generationCompleted(
                requiredUuid(payload, "tenantId"), requiredUuid(payload, "jobId"));
    }

    @RabbitListener(queues = CommunicationMessagingConfig.DOCUMENT_FAILED_QUEUE)
    public void failed(JsonNode payload) {
        attachments.generationFailed(
                requiredUuid(payload, "tenantId"),
                requiredUuid(payload, "jobId"),
                optionalText(payload, "errorCode"),
                optionalText(payload, "errorMessage"));
    }

    private UUID requiredUuid(JsonNode payload, String field) {
        String value = optionalText(payload, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return UUID.fromString(value);
    }

    private String optionalText(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
