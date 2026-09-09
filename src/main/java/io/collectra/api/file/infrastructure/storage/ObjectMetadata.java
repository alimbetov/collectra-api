package io.collectra.api.file.infrastructure.storage;

public record ObjectMetadata(long sizeBytes, String contentType, String eTag) {
    public ObjectMetadata {
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be non-negative");
    }
}
