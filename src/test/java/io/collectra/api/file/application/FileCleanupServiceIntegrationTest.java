package io.collectra.api.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import io.collectra.api.file.infrastructure.persistence.StoredFileRepository;
import io.collectra.api.file.infrastructure.storage.FileStorageException;
import io.collectra.api.file.infrastructure.storage.ObjectStorage;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class FileCleanupServiceIntegrationTest extends AbstractIntegrationTest {
    @Autowired FileCleanupService cleanupService;
    @Autowired StoredFileRepository files;
    @Autowired TenantRepository tenants;

    @MockitoBean ObjectStorage storage;

    @BeforeEach
    void resetStorage() {
        reset(storage);
    }

    @Test
    void concurrentRunsDoNotDeleteSameClaimedFileTwice() throws Exception {
        Instant now = Instant.now();
        StoredFile file = expiredReadyFile(now.minusSeconds(10));
        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);

        doAnswer(
                        invocation -> {
                            deleteStarted.countDown();
                            if (!releaseDelete.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError(
                                        "Timed out waiting to release storage delete");
                            }
                            return null;
                        })
                .when(storage)
                .delete(any());

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> cleanupService.cleanupExpiredFiles(now));
            assertThat(deleteStarted.await(5, TimeUnit.SECONDS)).isTrue();

            FileCleanupService.CleanupResult second = cleanupService.cleanupExpiredFiles(now);
            assertThat(second.processed()).isZero();

            releaseDelete.countDown();
            FileCleanupService.CleanupResult firstResult = first.get(5, TimeUnit.SECONDS);
            assertThat(firstResult.deleted()).isEqualTo(1);
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }

        verify(storage, times(1)).delete(any());
        StoredFile persisted = files.findById(file.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(FileStatus.DELETED);
    }

    @Test
    void failedDeleteIsRetriedOnlyAfterRetryDelay() {
        Instant now = Instant.now();
        StoredFile file = expiredReadyFile(now.minusSeconds(10));
        doThrow(new FileStorageException("rustfs unavailable", new RuntimeException("timeout")))
                .doNothing()
                .when(storage)
                .delete(any());

        FileCleanupService.CleanupResult first = cleanupService.cleanupExpiredFiles(now);
        assertThat(first.failed()).isEqualTo(1);
        assertThat(first.exhausted()).isZero();
        StoredFile failed = files.findById(file.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(FileStatus.DELETE_PENDING);
        assertThat(failed.getDeleteAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("rustfs unavailable");

        FileCleanupService.CleanupResult tooEarly =
                cleanupService.cleanupExpiredFiles(now.plusSeconds(60));
        assertThat(tooEarly.processed()).isZero();
        verify(storage, times(1)).delete(any());

        FileCleanupService.CleanupResult retry =
                cleanupService.cleanupExpiredFiles(now.plusSeconds(16 * 60));
        assertThat(retry.deleted()).isEqualTo(1);
        verify(storage, times(2)).delete(any());
        assertThat(files.findById(file.getId()).orElseThrow().getStatus())
                .isEqualTo(FileStatus.DELETED);
    }

    @Test
    void maxDeleteAttemptsMovesPoisonFileToDeleteFailedAndStopsFurtherClaims() {
        Instant base = Instant.now().minusSeconds(60 * 60);
        StoredFile file = expiredReadyFile(base.minusSeconds(10));
        doThrow(new FileStorageException("persistent failure", new RuntimeException("down")))
                .when(storage)
                .delete(any());

        FileCleanupService.CleanupResult last = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            last = cleanupService.cleanupExpiredFiles(base.plusSeconds(attempt * 16L * 60L));
        }

        assertThat(last).isNotNull();
        assertThat(last.exhausted()).isEqualTo(1);
        StoredFile exhausted = files.findById(file.getId()).orElseThrow();
        assertThat(exhausted.getDeleteAttempts()).isEqualTo(10);
        assertThat(exhausted.getStatus()).isEqualTo(FileStatus.DELETE_FAILED);
        assertThat(exhausted.getLastError()).contains("persistent failure");

        FileCleanupService.CleanupResult afterLimit =
                cleanupService.cleanupExpiredFiles(base.plusSeconds(11L * 16L * 60L));
        assertThat(afterLimit.processed()).isZero();
        verify(storage, times(10)).delete(any());
    }

    private StoredFile expiredReadyFile(Instant expiresAt) {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("cleanup-" + UUID.randomUUID(), "Cleanup Integration Tenant"));
        UUID fileId = UUID.randomUUID();
        StoredFile file =
                new StoredFile(
                        fileId,
                        tenant.getId(),
                        null,
                        FileCategory.TEMP,
                        "rustfs",
                        "collectra-temp",
                        "temp/" + tenant.getId() + "/" + fileId,
                        "expired.tmp",
                        "application/octet-stream",
                        expiresAt,
                        null);
        file.markReady(4, "application/octet-stream", "a".repeat(64));
        return files.saveAndFlush(file);
    }
}
