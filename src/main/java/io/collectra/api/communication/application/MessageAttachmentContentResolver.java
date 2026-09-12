package io.collectra.api.communication.application;

import io.collectra.api.communication.domain.MessageAttachment;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.document.application.DocumentObjectNotFoundException;
import io.collectra.api.document.application.GeneratedOutputService;
import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageAttachmentContentResolver {
    private final MessageAttachmentRepository attachments;
    private final GeneratedDocumentRepository documents;
    private final GeneratedOutputService outputs;
    private final AttachmentProperties properties;

    public MessageAttachmentContentResolver(
            MessageAttachmentRepository attachments,
            GeneratedDocumentRepository documents,
            GeneratedOutputService outputs,
            AttachmentProperties properties) {
        this.attachments = attachments;
        this.documents = documents;
        this.outputs = outputs;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttachment> resolve(UUID tenantId, UUID messageId) {
        List<MessageAttachment> relations =
                attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(tenantId, messageId);

        for (MessageAttachment relation : relations) {
            if (relation.isRequired() && relation.getStatus() != MessageAttachmentStatus.READY) {
                throw permanent(
                        "ATTACHMENT_NOT_READY", "Required attachment is not READY for delivery");
            }
        }

        List<MessageAttachment> ready =
                relations.stream()
                        .filter(relation -> relation.getStatus() == MessageAttachmentStatus.READY)
                        .toList();
        if (ready.size() > properties.getMaxCount()) {
            throw permanent("ATTACHMENT_COUNT_EXCEEDED", "Attachment count limit exceeded");
        }

        List<ResolvedMetadata> metadata = new ArrayList<>(ready.size());
        long total = 0;
        for (MessageAttachment relation : ready) {
            GeneratedDocument document = generatedDocument(tenantId, relation);
            long size = document.getSizeBytes();
            if (size < 0 || size > properties.getMaxFileSize().toBytes()) {
                throw permanent(
                        "ATTACHMENT_TOO_LARGE",
                        "Attachment exceeds max-file-size: " + relation.getFilename());
            }
            try {
                total = Math.addExact(total, size);
            } catch (ArithmeticException ex) {
                throw permanent("ATTACHMENT_TOTAL_SIZE_EXCEEDED", "Attachment total size overflow");
            }
            if (total > properties.getMaxTotalSize().toBytes()) {
                throw permanent(
                        "ATTACHMENT_TOTAL_SIZE_EXCEEDED", "Attachment total size limit exceeded");
            }
            metadata.add(new ResolvedMetadata(relation, document));
        }

        List<DeliveryAttachment> resolved = new ArrayList<>(metadata.size());
        for (ResolvedMetadata item : metadata) {
            byte[] content;
            try {
                content = outputs.read(item.document());
            } catch (DocumentObjectNotFoundException ex) {
                throw new AttachmentResolutionException(
                        "ATTACHMENT_OBJECT_MISSING",
                        DeliveryFailureKind.PERMANENT,
                        "Attachment object is missing",
                        ex);
            } catch (RuntimeException ex) {
                throw new AttachmentResolutionException(
                        "ATTACHMENT_STORAGE_READ_FAILED",
                        DeliveryFailureKind.RETRYABLE,
                        "Temporary attachment storage read failure",
                        ex);
            }
            if (content.length != item.document().getSizeBytes()) {
                throw permanent(
                        "ATTACHMENT_SIZE_MISMATCH",
                        "Attachment object size differs from persisted metadata");
            }
            resolved.add(
                    new DeliveryAttachment(
                            safeFilename(item.relation().getFilename()),
                            item.relation().getContentType(),
                            content));
        }
        return List.copyOf(resolved);
    }

    private GeneratedDocument generatedDocument(UUID tenantId, MessageAttachment relation) {
        UUID generatedDocumentId = relation.getGeneratedDocumentId();
        if (generatedDocumentId == null) {
            throw permanent(
                    "ATTACHMENT_METADATA_MISSING",
                    "READY attachment has no generated document reference");
        }
        GeneratedDocument document =
                documents
                        .findByIdAndTenantId(generatedDocumentId, tenantId)
                        .orElseThrow(
                                () ->
                                        new AttachmentResolutionException(
                                                "ATTACHMENT_NOT_FOUND",
                                                DeliveryFailureKind.PERMANENT,
                                                "Generated attachment metadata not found"));
        if (!relation.getGenerationJobId().equals(document.getGenerationJobId())
                || relation.getOutputFormat() != document.getFormat()) {
            throw permanent(
                    "ATTACHMENT_METADATA_MISMATCH",
                    "Generated attachment metadata does not match requirement");
        }
        return document;
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw permanent("INVALID_ATTACHMENT_FILENAME", "Attachment filename is required");
        }
        String normalized = filename.trim();
        if (normalized.contains("\r")
                || normalized.contains("\n")
                || normalized.contains("/")
                || normalized.contains("\\")) {
            throw permanent("INVALID_ATTACHMENT_FILENAME", "Attachment filename is unsafe");
        }
        return normalized;
    }

    private AttachmentResolutionException permanent(String code, String message) {
        return new AttachmentResolutionException(code, DeliveryFailureKind.PERMANENT, message);
    }

    private record ResolvedMetadata(MessageAttachment relation, GeneratedDocument document) {}
}
