package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileCategory;
import java.io.InputStream;
import java.util.UUID;

public record UploadFileCommand(
        UUID tenantId,
        UUID projectId,
        FileCategory category,
        String originalFilename,
        String contentType,
        long contentLength,
        InputStream content,
        UUID createdBy) {
    public UploadFileCommand {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (category == null) throw new IllegalArgumentException("category is required");
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename is required");
        }
        if (contentLength < 0) throw new IllegalArgumentException("contentLength must be non-negative");
        if (content == null) throw new IllegalArgumentException("content is required");
    }
}
