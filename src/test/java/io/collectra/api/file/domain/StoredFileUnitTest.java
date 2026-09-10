package io.collectra.api.file.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoredFileUnitTest {
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-09-09T00:00:00Z");

    @Test
    void shouldFollowUploadAndDeleteLifecycle() {
        StoredFile file = readyFile();

        file.markDeletePending();
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETE_PENDING);

        boolean exhausted = file.registerDeleteFailure(ATTEMPTED_AT, "timeout", 3);
        assertThat(exhausted).isFalse();
        assertThat(file.getDeleteAttempts()).isEqualTo(1);
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETE_PENDING);

        file.markDeleted(ATTEMPTED_AT.plusSeconds(60));
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(file.getDeletedAt()).isEqualTo(ATTEMPTED_AT.plusSeconds(60));
        assertThat(file.getLastError()).isNull();
    }

    @Test
    void deleteFailureBelowLimitKeepsDeletePending() {
        StoredFile file = readyFile();
        file.claimDeleteAttempt(ATTEMPTED_AT);

        boolean exhausted = file.registerDeleteFailure(ATTEMPTED_AT, "temporary outage", 2);

        assertThat(exhausted).isFalse();
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETE_PENDING);
        assertThat(file.getDeleteAttempts()).isEqualTo(1);
        assertThat(file.getLastDeleteAttemptAt()).isEqualTo(ATTEMPTED_AT);
        assertThat(file.getLastError()).isEqualTo("temporary outage");
    }

    @Test
    void deleteFailureAtLimitMovesToDeleteFailed() {
        StoredFile file = readyFile();
        file.claimDeleteAttempt(ATTEMPTED_AT);
        file.registerDeleteFailure(ATTEMPTED_AT, "first failure", 2);
        file.claimDeleteAttempt(ATTEMPTED_AT.plusSeconds(60));

        boolean exhausted = file.registerDeleteFailure(
                ATTEMPTED_AT.plusSeconds(60), "persistent failure", 2);

        assertThat(exhausted).isTrue();
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETE_FAILED);
        assertThat(file.getDeleteAttempts()).isEqualTo(2);
        assertThat(file.getLastDeleteAttemptAt()).isEqualTo(ATTEMPTED_AT.plusSeconds(60));
        assertThat(file.getLastError()).isEqualTo("persistent failure");
    }

    @Test
    void cannotMarkDeleteFailedFileAsDeleted() {
        StoredFile file = readyFile();
        file.claimDeleteAttempt(ATTEMPTED_AT);
        file.registerDeleteFailure(ATTEMPTED_AT, "persistent failure", 1);

        assertThatThrownBy(() -> file.markDeleted(ATTEMPTED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalFileStateException.class);
    }

    @Test
    void registerDeleteFailureRejectsInvalidMaxAttempts() {
        StoredFile file = readyFile();
        file.claimDeleteAttempt(ATTEMPTED_AT);

        assertThatThrownBy(() -> file.registerDeleteFailure(ATTEMPTED_AT, "failure", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDeleteAttempts");
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

    private StoredFile readyFile() {
        StoredFile file = file();
        file.markReady(12, "text/plain", "a".repeat(64));
        return file;
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
