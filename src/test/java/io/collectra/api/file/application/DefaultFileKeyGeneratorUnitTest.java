package io.collectra.api.file.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.file.domain.FileCategory;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultFileKeyGeneratorUnitTest {

    private final DefaultFileKeyGenerator generator = new DefaultFileKeyGenerator();

    @Test
    void shouldGenerateDeterministicUtcKeyWithoutFilename() {
        UUID tenantId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID projectId = UUID.fromString("650e8400-e29b-41d4-a716-446655440000");
        UUID fileId = UUID.fromString("750e8400-e29b-41d4-a716-446655440000");

        String key =
                generator.generate(
                        FileCategory.REPORT,
                        tenantId,
                        projectId,
                        fileId,
                        Instant.parse("2026-09-09T23:59:59Z"));

        assertThat(key)
                .isEqualTo(
                        "reports/550e8400-e29b-41d4-a716-446655440000/"
                                + "650e8400-e29b-41d4-a716-446655440000/2026/09/"
                                + "750e8400-e29b-41d4-a716-446655440000");
        assertThat(key).doesNotContain(".pdf", "..");
    }

    @Test
    void shouldUseGlobalProjectSegmentWhenProjectIsAbsent() {
        String key =
                generator.generate(
                        FileCategory.ASSET,
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(key).contains("/global/2026/01/");
    }
}
