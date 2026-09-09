package io.collectra.api.file.application;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

public record PresignedDownload(UUID fileId, URI url, Duration ttl) {
    public PresignedDownload {
        if (fileId == null) throw new IllegalArgumentException("fileId is required");
        if (url == null) throw new IllegalArgumentException("url is required");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }
}
