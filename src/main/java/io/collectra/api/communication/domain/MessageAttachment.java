package io.collectra.api.communication.domain;

import io.collectra.api.document.domain.OutputFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "message_attachments")
public class MessageAttachment {
    private static final int FILENAME_MAX_LENGTH = 255;
    private static final int CONTENT_TYPE_MAX_LENGTH = 150;
    private static final int FAILURE_CODE_MAX_LENGTH = 80;
    private static final int FAILURE_MESSAGE_MAX_LENGTH = 1000;

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "generation_job_id", nullable = false)
    private UUID generationJobId;

    @Column(name = "generated_document_id")
    private UUID generatedDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, length = 20)
    private OutputFormat outputFormat;

    @Column(nullable = false, length = FILENAME_MAX_LENGTH)
    private String filename;

    @Column(name = "content_type", nullable = false, length = CONTENT_TYPE_MAX_LENGTH)
    private String contentType;

    @Column(nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageAttachmentStatus status;

    @Column(name = "failure_code", length = FAILURE_CODE_MAX_LENGTH)
    private String failureCode;

    @Column(name = "failure_message", length = FAILURE_MESSAGE_MAX_LENGTH)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    protected MessageAttachment() {}

    private MessageAttachment(
            UUID tenantId,
            UUID messageId,
            UUID generationJobId,
            OutputFormat outputFormat,
            String filename,
            String contentType,
            boolean required,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.messageId = Objects.requireNonNull(messageId, "messageId is required");
        this.generationJobId =
                Objects.requireNonNull(generationJobId, "generationJobId is required");
        this.outputFormat = Objects.requireNonNull(outputFormat, "outputFormat is required");
        if (outputFormat != OutputFormat.PDF) {
            throw new IllegalArgumentException("Slice 7 supports PDF attachments only");
        }
        this.filename = required(filename, "filename", FILENAME_MAX_LENGTH);
        this.contentType = required(contentType, "contentType", CONTENT_TYPE_MAX_LENGTH);
        this.required = required;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.status = MessageAttachmentStatus.PENDING;
    }

    public static MessageAttachment pendingPdf(
            UUID tenantId,
            UUID messageId,
            UUID generationJobId,
            String filename,
            boolean required,
            Instant createdAt) {
        return new MessageAttachment(
                tenantId,
                messageId,
                generationJobId,
                OutputFormat.PDF,
                filename,
                "application/pdf",
                required,
                createdAt);
    }

    public boolean markReady(UUID generatedDocumentId, Instant now) {
        Objects.requireNonNull(generatedDocumentId, "generatedDocumentId is required");
        Objects.requireNonNull(now, "now is required");
        if (status == MessageAttachmentStatus.READY) {
            if (!generatedDocumentId.equals(this.generatedDocumentId)) {
                throw new IllegalStateException("Attachment is already bound to another generated document");
            }
            return false;
        }
        if (status == MessageAttachmentStatus.FAILED) {
            return false;
        }
        this.generatedDocumentId = generatedDocumentId;
        this.status = MessageAttachmentStatus.READY;
        this.readyAt = now;
        this.failureCode = null;
        this.failureMessage = null;
        return true;
    }

    public boolean markFailed(String failureCode, String failureMessage, Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (status != MessageAttachmentStatus.PENDING) {
            return false;
        }
        this.status = MessageAttachmentStatus.FAILED;
        this.generatedDocumentId = null;
        this.readyAt = null;
        this.failedAt = now;
        this.failureCode = required(failureCode, "failureCode", FAILURE_CODE_MAX_LENGTH);
        this.failureMessage = optional(failureMessage, FAILURE_MESSAGE_MAX_LENGTH);
        return true;
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getGenerationJobId() {
        return generationJobId;
    }

    public UUID getGeneratedDocumentId() {
        return generatedDocumentId;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isRequired() {
        return required;
    }

    public MessageAttachmentStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
