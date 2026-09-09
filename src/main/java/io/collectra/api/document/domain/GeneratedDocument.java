package io.collectra.api.document.domain;

import jakarta.persistence.*;

import java.time.Instant;
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
            String sha256) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.generationJobId = jobId;
        this.format = format;
        this.storageKey = storageKey;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
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
}
