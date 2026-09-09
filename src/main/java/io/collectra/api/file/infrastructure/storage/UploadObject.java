package io.collectra.api.file.infrastructure.storage;

import java.io.InputStream;
import java.util.Map;

public record UploadObject(
        StorageLocation location,
        InputStream content,
        long contentLength,
        String contentType,
        Map<String, String> metadata) {
    public UploadObject {
        if (location == null) throw new IllegalArgumentException("location is required");
        if (content == null) throw new IllegalArgumentException("content is required");
        if (contentLength < 0) throw new IllegalArgumentException("contentLength must be non-negative");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
