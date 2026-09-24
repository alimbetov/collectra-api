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
@Table(name = "message_document_links")
public class MessageDocumentLink {
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

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageDocumentLinkStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    protected MessageDocumentLink() {}

    public MessageDocumentLink(
            UUID tenantId,
            UUID messageId,
            UUID generationJobId,
            String tokenHash,
            boolean required,
            Instant expiresAt,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.messageId = Objects.requireNonNull(messageId, "messageId is required");
        this.generationJobId =
                Objects.requireNonNull(generationJobId, "generationJobId is required");
        this.outputFormat = OutputFormat.PDF;
        this.tokenHash = required(tokenHash, "tokenHash", 64);
        this.required = required;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.status = MessageDocumentLinkStatus.PENDING;
    }

    public boolean markReady(UUID generatedDocumentId, Instant now) {
        Objects.requireNonNull(generatedDocumentId, "generatedDocumentId is required");
        Objects.requireNonNull(now, "now is required");
        if (status == MessageDocumentLinkStatus.READY) {
            return false;
        }
        if (status == MessageDocumentLinkStatus.FAILED) {
            return false;
        }
        this.generatedDocumentId = generatedDocumentId;
        this.status = MessageDocumentLinkStatus.READY;
        this.readyAt = now;
        return true;
    }

    public boolean markFailed(String code, String message, Instant now) {
        if (status != MessageDocumentLinkStatus.PENDING) {
            return false;
        }
        this.status = MessageDocumentLinkStatus.FAILED;
        this.failureCode = required(code, "failureCode", 80);
        this.failureMessage = optional(message, 1000);
        this.failedAt = Objects.requireNonNull(now, "now is required");
        return true;
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

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isRequired() {
        return required;
    }

    public MessageDocumentLinkStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
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
}
