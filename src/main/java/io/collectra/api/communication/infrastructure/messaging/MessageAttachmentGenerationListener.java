package io.collectra.api.communication.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.communication.application.MessageAttachmentService;
import io.collectra.api.communication.application.MessageDocumentLinkService;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MessageAttachmentGenerationListener {
    private final MessageAttachmentService attachments;
    private final MessageDocumentLinkService documentLinks;

    public MessageAttachmentGenerationListener(
            MessageAttachmentService attachments, MessageDocumentLinkService documentLinks) {
        this.attachments = attachments;
        this.documentLinks = documentLinks;
    }

    @RabbitListener(queues = CommunicationMessagingConfig.DOCUMENT_COMPLETED_QUEUE)
    public void completed(JsonNode payload) {
        UUID tenantId = requiredUuid(payload, "tenantId");
        UUID jobId = requiredUuid(payload, "jobId");
        attachments.generationCompleted(tenantId, jobId);
        documentLinks.generationCompleted(tenantId, jobId);
    }

    @RabbitListener(queues = CommunicationMessagingConfig.DOCUMENT_FAILED_QUEUE)
    public void failed(JsonNode payload) {
        UUID tenantId = requiredUuid(payload, "tenantId");
        UUID jobId = requiredUuid(payload, "jobId");
        String errorCode = optionalText(payload, "errorCode");
        String errorMessage = optionalText(payload, "errorMessage");
        attachments.generationFailed(tenantId, jobId, errorCode, errorMessage);
        documentLinks.generationFailed(tenantId, jobId, errorCode, errorMessage);
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
