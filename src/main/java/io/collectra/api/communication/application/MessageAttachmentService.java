package io.collectra.api.communication.application;

import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachment;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageAttachmentService {
    public static final String ATTACHMENT_GENERATION_FAILED = "ATTACHMENT_GENERATION_FAILED";

    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final GeneratedDocumentRepository documents;
    private final CampaignRunRepository runs;
    private final MessageDeliveryRequestService deliveryRequests;
    private final Clock clock;

    public MessageAttachmentService(
            MessageRepository messages,
            MessageAttachmentRepository attachments,
            GeneratedDocumentRepository documents,
            CampaignRunRepository runs,
            MessageDeliveryRequestService deliveryRequests,
            Clock clock) {
        this.messages = messages;
        this.attachments = attachments;
        this.documents = documents;
        this.runs = runs;
        this.deliveryRequests = deliveryRequests;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MessageAttachment createPendingGeneratedPdf(
            Message message, GenerationJob job, String filename, boolean required) {
        Objects.requireNonNull(message, "message is required");
        Objects.requireNonNull(job, "job is required");
        if (!message.getTenantId().equals(job.getTenantId())) {
            throw new IllegalArgumentException("Message and generation job tenants differ");
        }
        if (required && message.getDeliveryRequestedAt() != null) {
            throw new IllegalStateException(
                    "Required attachment cannot be added after delivery became eligible");
        }
        if (message.getStatus() != MessageStatus.QUEUED) {
            throw new IllegalStateException(
                    "Attachment can only be materialized for QUEUED message");
        }
        return attachments.save(
                MessageAttachment.pendingPdf(
                        message.getTenantId(),
                        message.getId(),
                        job.getId(),
                        filename,
                        required,
                        clock.instant()));
    }

    @Transactional
    public boolean generationCompleted(UUID tenantId, UUID generationJobId) {
        MessageAttachment correlation =
                attachments
                        .findByTenantIdAndGenerationJobId(tenantId, generationJobId)
                        .orElse(null);
        if (correlation == null) {
            return false;
        }

        // Normative lock order: Message -> MessageAttachment -> CampaignRun (if ever needed).
        Message message = lockedMessage(tenantId, correlation.getMessageId());
        MessageAttachment attachment = lockedAttachment(tenantId, generationJobId);
        if (!message.getId().equals(attachment.getMessageId())) {
            throw new IllegalStateException("Attachment correlation changed while locking");
        }
        if (attachment.getStatus() != MessageAttachmentStatus.PENDING) {
            return false;
        }

        GeneratedDocument document =
                documents
                        .findByTenantIdAndGenerationJobIdAndFormat(
                                tenantId, generationJobId, attachment.getOutputFormat())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Generated output metadata is missing for completed job"));
        if (!document.getFormat().equals(attachment.getOutputFormat())) {
            throw new IllegalStateException(
                    "Generated output format differs from attachment requirement");
        }

        attachment.markReady(document.getId(), clock.instant());
        deliveryRequests.requestIfEligible(message);
        return true;
    }

    @Transactional
    public boolean generationFailed(
            UUID tenantId, UUID generationJobId, String errorCode, String errorMessage) {
        MessageAttachment correlation =
                attachments
                        .findByTenantIdAndGenerationJobId(tenantId, generationJobId)
                        .orElse(null);
        if (correlation == null) {
            return false;
        }

        Message message = lockedMessage(tenantId, correlation.getMessageId());
        MessageAttachment attachment = lockedAttachment(tenantId, generationJobId);
        if (!message.getId().equals(attachment.getMessageId())) {
            throw new IllegalStateException("Attachment correlation changed while locking");
        }
        if (!attachment.markFailed(
                errorCode == null || errorCode.isBlank() ? "GENERATION_FAILED" : errorCode,
                errorMessage,
                clock.instant())) {
            return false;
        }

        if (!attachment.isRequired()) {
            // Optional PENDING/FAILED is explicitly non-blocking.
            deliveryRequests.requestIfEligible(message);
            return true;
        }

        if (message.getStatus() != MessageStatus.QUEUED) {
            return true;
        }
        CampaignRun run =
                runs.findLockedByIdAndTenantId(message.getCampaignRunId(), tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Campaign run not found"));
        message.markFailedBeforeDelivery(
                ATTACHMENT_GENERATION_FAILED,
                errorMessage == null || errorMessage.isBlank()
                        ? "Required attachment generation failed"
                        : errorMessage);
        run.messageFailed();
        run.completeIfTerminal(clock.instant());
        return true;
    }

    private Message lockedMessage(UUID tenantId, UUID messageId) {
        return messages.findLockedByIdAndTenantId(tenantId, messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found"));
    }

    private MessageAttachment lockedAttachment(UUID tenantId, UUID generationJobId) {
        return attachments
                .findLockedByTenantIdAndGenerationJobId(tenantId, generationJobId)
                .orElseThrow(() -> new NoSuchElementException("Message attachment not found"));
    }
}
