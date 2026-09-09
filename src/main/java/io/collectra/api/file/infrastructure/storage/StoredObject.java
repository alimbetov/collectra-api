package io.collectra.api.file.infrastructure.storage;

public record StoredObject(long sizeBytes, String eTag) {
    public StoredObject {
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be non-negative");
    }
}
