package io.collectra.api.file.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoredFileUnitTest {

    @Test
    void shouldFollowUploadAndDeleteLifecycle() {
        StoredFile file = file();

        file.markReady(12, "text/plain", "a".repeat(64));
        assertThat(file.getStatus()).isEqualTo(FileStatus.READY);
        assertThat(file.getSizeBytes()).isEqualTo(12);

        file.markDeletePending();
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETE_PENDING);

        file.registerDeleteFailure(Instant.parse("2026-09-09T00:00:00Z"), "timeout");
        assertThat(file.getDeleteAttempts()).isEqualTo(1);
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETE_PENDING);

        file.markDeleted(Instant.parse("2026-09-09T00:01:00Z"));
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(file.getDeletedAt()).isNotNull();
    }

    @Test
    void shouldRejectInvalidTransition() {
        StoredFile file = file();

        assertThatThrownBy(file::markDeletePending)
                .isInstanceOf(IllegalFileStateException.class);
    }

    @Test
    void shouldPreserveFailedMetadataState() {
        StoredFile file = file();

        file.markFailed("storage unavailable");

        assertThat(file.getStatus()).isEqualTo(FileStatus.FAILED);
        assertThat(file.getLastError()).isEqualTo("storage unavailable");
    }

    private StoredFile file() {
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        return new StoredFile(
                fileId,
                tenantId,
                null,
                FileCategory.IMPORT_SOURCE,
                "rustfs",
                "collectra-source",
                "imports/" + tenantId + "/global/2026/09/" + fileId,
                "sample.txt",
                "text/plain",
                Instant.parse("2026-12-08T00:00:00Z"),
                null);
    }
}
