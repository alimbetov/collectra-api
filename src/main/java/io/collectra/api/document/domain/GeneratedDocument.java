package io.collectra.api.document.domain;

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
@Table(name = "generated_documents")
public class GeneratedDocument {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "generation_job_id", nullable = false)
    private UUID generationJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OutputFormat format;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "media_type", nullable = false, length = 100)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GeneratedDocument() {}

    public GeneratedDocument(
            UUID tenantId,
            UUID jobId,
            OutputFormat format,
            String storageKey,
            String mediaType,
            long sizeBytes,
            String sha256,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.generationJobId = Objects.requireNonNull(jobId, "jobId is required");
        this.format = Objects.requireNonNull(format, "format is required");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey is required");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType is required");
        this.sizeBytes = sizeBytes;
        this.sha256 = Objects.requireNonNull(sha256, "sha256 is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getGenerationJobId() {
        return generationJobId;
    }

    public OutputFormat getFormat() {
        return format;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMediaType() {
        return mediaType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
