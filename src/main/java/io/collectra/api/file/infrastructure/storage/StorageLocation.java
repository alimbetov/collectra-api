package io.collectra.api.file.infrastructure.storage;

public record StorageLocation(String bucket, String objectKey) {
    public StorageLocation {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("objectKey is required");
    }
}
