package io.collectra.api.file.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface ObjectStorage {
    StoredObject upload(UploadObject command);

    InputStream download(StorageLocation location);

    ObjectMetadata stat(StorageLocation location);

    void delete(StorageLocation location);

    boolean exists(StorageLocation location);

    URI generatePresignedGetUrl(StorageLocation location, Duration ttl);

    URI generatePresignedPutUrl(StorageLocation location, String contentType, Duration ttl);
}
