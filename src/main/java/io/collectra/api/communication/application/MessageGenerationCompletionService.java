package io.collectra.api.communication.application;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MessageGenerationCompletionService {
    private final MessageAttachmentService attachments;
    private final MessageDocumentLinkService documentLinks;

    public MessageGenerationCompletionService(
            MessageAttachmentService attachments, MessageDocumentLinkService documentLinks) {
        this.attachments = attachments;
        this.documentLinks = documentLinks;
    }

    public void completed(UUID tenantId, UUID generationJobId) {
        attachments.generationCompleted(tenantId, generationJobId);
        documentLinks.generationCompleted(tenantId, generationJobId);
    }

    public void failed(
            UUID tenantId, UUID generationJobId, String errorCode, String errorMessage) {
        attachments.generationFailed(tenantId, generationJobId, errorCode, errorMessage);
        documentLinks.generationFailed(tenantId, generationJobId, errorCode, errorMessage);
    }
}
