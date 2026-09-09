package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileCategory;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultFileKeyGenerator implements FileKeyGenerator {

    @Override
    public String generate(
            FileCategory category,
            UUID tenantId,
            UUID projectId,
            UUID fileId,
            Instant createdAt) {
        if (category == null || tenantId == null || fileId == null || createdAt == null) {
            throw new IllegalArgumentException("category, tenantId, fileId and createdAt are required");
        }

        var utc = createdAt.atZone(ZoneOffset.UTC);
        String prefix = switch (category) {
            case IMPORT_SOURCE -> "imports";
            case REPORT -> "reports";
            case EXPORT -> "exports";
            case ASSET -> "assets";
            case TEMP -> "temp";
        };
        String projectSegment = projectId == null ? "global" : projectId.toString();
        String key = String.format(
                Locale.ROOT,
                "%s/%s/%s/%04d/%02d/%s",
                prefix,
                tenantId,
                projectSegment,
                utc.getYear(),
                utc.getMonthValue(),
                fileId);

        if (key.startsWith("/") || key.contains("..")) {
            throw new IllegalStateException("Generated object key is unsafe");
        }
        return key;
    }
}
