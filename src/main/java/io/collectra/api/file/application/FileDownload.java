package io.collectra.api.file.application;

import java.io.InputStream;

public record FileDownload(FileMetadata metadata, InputStream content) {
    public FileDownload {
        if (metadata == null) throw new IllegalArgumentException("metadata is required");
        if (content == null) throw new IllegalArgumentException("content is required");
    }
}
