package io.collectra.api.document.application;

public interface DocumentStorage {
    StoredObject put(String key, byte[] content, String mediaType);

    byte[] get(String key);

    record StoredObject(String key, String mediaType, long sizeBytes, String sha256) {}
}
