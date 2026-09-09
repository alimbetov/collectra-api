package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileCategory;
import java.time.Instant;
import java.util.UUID;

public interface FileKeyGenerator {
    String generate(
            FileCategory category,
            UUID tenantId,
            UUID projectId,
            UUID fileId,
            Instant createdAt);
}
