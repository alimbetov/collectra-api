package io.collectra.api.communication.application;

import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachment;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MessageQueryService {
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    private static final Sort MESSAGE_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final CampaignRunRepository runs;
    private final DestinationMasker destinationMasker;
    private final DeliveryErrorSummary errorSummary;

    public MessageQueryService(
            MessageRepository messages,
            MessageAttachmentRepository attachments,
            CampaignRunRepository runs,
            DestinationMasker destinationMasker,
            DeliveryErrorSummary errorSummary) {
        this.messages = messages;
        this.attachments = attachments;
        this.runs = runs;
        this.destinationMasker = destinationMasker;
        this.errorSummary = errorSummary;
    }

    public MessageSlice list(
            UUID tenantId,
            UUID campaignId,
            UUID runId,
            MessageStatus status,
            CommunicationChannel channel,
            UUID customerId,
            int page,
            int size) {
        validatePage(page, size);
        requireRun(tenantId, campaignId, runId);
        var result =
                messages.findDeliveryMessages(
                        tenantId,
                        campaignId,
                        runId,
                        status,
                        channel,
                        customerId,
                        PageRequest.of(page, size, MESSAGE_SORT));
        return new MessageSlice(
                result.getContent().stream().map(this::listItem).toList(),
                page,
                size,
                result.hasNext());
    }

    public MessageDetail detail(UUID tenantId, UUID campaignId, UUID runId, UUID messageId) {
        Message message =
                messages.findByIdAndTenantIdAndCampaignIdAndCampaignRunId(
                                messageId, tenantId, campaignId, runId)
                        .orElseThrow(() -> new NoSuchElementException("Message not found"));
        List<AttachmentDetail> attachmentDetails =
                attachments
                        .findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(tenantId, messageId)
                        .stream()
                        .map(this::attachment)
                        .toList();
        String normalizedCode = errorSummary.normalizeCode(message.getLastErrorCode());
        return new MessageDetail(
                message.getId(),
                message.getCampaignId(),
                message.getCampaignRunId(),
                message.getCustomerId(),
                message.getInvoiceId(),
                message.getTemplateVersionId(),
                message.getChannel(),
                destinationMasker.mask(message.getChannel(), message.getDestination()),
                message.getResolvedLocale(),
                message.getStatus(),
                message.getAttemptCount(),
                message.getNextRetryAt(),
                message.getSentAt(),
                message.getProcessingStartedAt(),
                message.getProviderMessageId(),
                normalizedCode,
                errorSummary.summary(normalizedCode),
                message.getCreatedAt(),
                attachmentDetails);
    }

    private void requireRun(UUID tenantId, UUID campaignId, UUID runId) {
        runs.findByIdAndTenantId(runId, tenantId)
                .filter(value -> value.getCampaignId().equals(campaignId))
                .orElseThrow(() -> new NoSuchElementException("Campaign run not found"));
    }

    private MessageListItem listItem(Message message) {
        return new MessageListItem(
                message.getId(),
                message.getCampaignRunId(),
                message.getCustomerId(),
                message.getChannel(),
                destinationMasker.mask(message.getChannel(), message.getDestination()),
                message.getStatus(),
                message.getAttemptCount(),
                message.getNextRetryAt(),
                message.getSentAt(),
                message.getCreatedAt());
    }

    private AttachmentDetail attachment(MessageAttachment value) {
        return new AttachmentDetail(
                value.getId(),
                value.getFilename(),
                value.getContentType(),
                null,
                value.isRequired(),
                value.getStatus());
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public record MessageSlice(
            List<MessageListItem> content, int page, int size, boolean hasNext) {}

    public record MessageListItem(
            UUID id,
            UUID campaignRunId,
            UUID customerId,
            CommunicationChannel channel,
            String maskedDestination,
            MessageStatus status,
            int attemptCount,
            Instant nextRetryAt,
            Instant sentAt,
            Instant createdAt) {}

    public record MessageDetail(
            UUID id,
            UUID campaignId,
            UUID campaignRunId,
            UUID customerId,
            UUID invoiceId,
            UUID templateVersionId,
            CommunicationChannel channel,
            String maskedDestination,
            String resolvedLocale,
            MessageStatus status,
            int attemptCount,
            Instant nextRetryAt,
            Instant sentAt,
            Instant processingStartedAt,
            String providerMessageId,
            String lastErrorCode,
            String lastErrorSummary,
            Instant createdAt,
            List<AttachmentDetail> attachments) {}

    public record AttachmentDetail(
            UUID id,
            String filename,
            String contentType,
            Long size,
            boolean required,
            MessageAttachmentStatus status) {}
}
