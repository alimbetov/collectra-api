package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.file.infrastructure.storage.FileStorageProperties;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class FileRetentionPolicy {
    private final FileStorageProperties properties;

    public FileRetentionPolicy(FileStorageProperties properties) {
        this.properties = properties;
    }

    public Instant expiresAt(FileCategory category, Instant createdAt) {
        if (category == null || createdAt == null) {
            throw new IllegalArgumentException("category and createdAt are required");
        }
        return switch (category) {
            case IMPORT_SOURCE -> createdAt.plus(properties.getRetention().getImportSource());
            case REPORT -> createdAt.plus(properties.getRetention().getReport());
            case EXPORT -> createdAt.plus(properties.getRetention().getExport());
            case TEMP -> createdAt.plus(properties.getRetention().getTemp());
            case ASSET -> null;
        };
    }
}
