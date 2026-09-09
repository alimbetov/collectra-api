package io.collectra.api.file.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_file")
public class StoredFile extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FileCategory category;

    @Column(name = "storage_provider", nullable = false, length = 30)
    private String storageProvider;

    @Column(nullable = false, length = 128)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 512)
    private String originalFilename;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FileStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "delete_attempts", nullable = false)
    private int deleteAttempts;

    @Column(name = "last_delete_attempt_at")
    private Instant lastDeleteAttemptAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    protected StoredFile() {}

    public StoredFile(
            UUID id,
            UUID tenantId,
            UUID projectId,
            FileCategory category,
            String storageProvider,
            String bucket,
            String objectKey,
            String originalFilename,
            String contentType,
            Instant expiresAt,
            UUID createdBy) {
        this.id = require(id, "id");
        this.tenantId = require(tenantId, "tenantId");
        this.projectId = projectId;
        this.category = require(category, "category");
        this.storageProvider = requireText(storageProvider, "storageProvider", 30);
        this.bucket = requireText(bucket, "bucket", 128);
        this.objectKey = requireText(objectKey, "objectKey", 1024);
        this.originalFilename = sanitizeFilename(originalFilename);
        this.contentType = normalizeText(contentType, 255);
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.status = FileStatus.UPLOADING;
    }

    public void markReady(long sizeBytes, String contentType, String checksumSha256) {
        requireStatus(FileStatus.UPLOADING, "mark ready");
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be non-negative");
        String checksum = requireText(checksumSha256, "checksumSha256", 64);
        if (!checksum.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("checksumSha256 must contain 64 hexadecimal characters");
        }
        this.sizeBytes = sizeBytes;
        this.contentType = normalizeText(contentType, 255);
        this.checksumSha256 = checksum.toLowerCase(java.util.Locale.ROOT);
        this.status = FileStatus.READY;
        this.lastError = null;
    }

    public void markFailed(String error) {
        requireStatus(FileStatus.UPLOADING, "mark failed");
        this.status = FileStatus.FAILED;
        this.lastError = sanitizeError(error);
    }

    public void markDeletePending() {
        if (status == FileStatus.DELETE_PENDING) return;
        requireStatus(FileStatus.READY, "mark delete pending");
        this.status = FileStatus.DELETE_PENDING;
    }

    public void registerDeleteFailure(Instant attemptedAt, String error) {
        requireStatus(FileStatus.DELETE_PENDING, "register delete failure");
        this.deleteAttempts++;
        this.lastDeleteAttemptAt = require(attemptedAt, "attemptedAt");
        this.lastError = sanitizeError(error);
    }

    public void markDeleted(Instant deletedAt) {
        if (status == FileStatus.DELETED) return;
        requireStatus(FileStatus.DELETE_PENDING, "mark deleted");
        this.status = FileStatus.DELETED;
        this.deletedAt = require(deletedAt, "deletedAt");
        this.lastError = null;
    }

    private void requireStatus(FileStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalFileStateException(
                    "Cannot " + operation + " file " + id + " from status " + status);
        }
    }

    private static String sanitizeFilename(String value) {
        String sanitized = requireText(value, "originalFilename", 512)
                .replaceAll("[\\r\\n\\t\\x00-\\x1F\\x7F]", "_");
        return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
    }

    private static String sanitizeError(String value) {
        String safe = value == null || value.isBlank() ? "File operation failed" : value;
        safe = safe.replaceAll("[\\r\\n\\t]", " ");
        return safe.length() <= 2000 ? safe : safe.substring(0, 2000);
    }

    private static String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static <T> T require(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public FileCategory getCategory() { return category; }
    public String getStorageProvider() { return storageProvider; }
    public String getBucket() { return bucket; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public FileStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public int getDeleteAttempts() { return deleteAttempts; }
    public Instant getLastDeleteAttemptAt() { return lastDeleteAttemptAt; }
    public String getLastError() { return lastError; }
}
