package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import java.time.Instant;
import java.util.UUID;

public record FileMetadata(
        UUID fileId,
        UUID tenantId,
        UUID projectId,
        FileCategory category,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String checksumSha256,
        FileStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant deletedAt) {

    public static FileMetadata from(StoredFile file) {
        return new FileMetadata(
                file.getId(),
                file.getTenantId(),
                file.getProjectId(),
                file.getCategory(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getChecksumSha256(),
                file.getStatus(),
                file.getCreatedAt(),
                file.getExpiresAt(),
                file.getDeletedAt());
    }
}
