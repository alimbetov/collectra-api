package io.collectra.api.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import io.collectra.api.file.infrastructure.persistence.StoredFileRepository;
import io.collectra.api.file.infrastructure.storage.FileStorageException;
import io.collectra.api.file.infrastructure.storage.FileStorageProperties;
import io.collectra.api.file.infrastructure.storage.ObjectStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class FileCleanupServiceUnitTest {
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    @Test
    void successfulDeletionIncrementsProcessedAndDeletedCounters() {
        StoredFile file = readyFile();
        StoredFileRepository files = repositoryFor(file);
        ObjectStorage storage = mock(ObjectStorage.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FileCleanupService service = service(files, storage, registry);

        FileCleanupService.CleanupResult result = service.cleanupExpiredFiles(NOW);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.exhausted()).isZero();
        assertThat(registry.get("collectra.file.cleanup.processed").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("collectra.file.cleanup.deleted").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("collectra.file.cleanup.failed").counter().count()).isZero();
        assertThat(registry.get("collectra.file.cleanup.exhausted").counter().count()).isZero();
    }

    @Test
    void finalFailureIncrementsFailedAndExhaustedCounters() {
        StoredFile file = readyFile();
        for (int attempt = 1; attempt < 10; attempt++) {
            Instant at = NOW.minusSeconds((10L - attempt) * 60L);
            file.claimDeleteAttempt(at);
            file.registerDeleteFailure(at, "failure-" + attempt, 10);
        }

        StoredFileRepository files = repositoryFor(file);
        ObjectStorage storage = mock(ObjectStorage.class);
        doThrow(new FileStorageException("rustfs unavailable", new RuntimeException("down")))
                .when(storage)
                .delete(any());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FileCleanupService service = service(files, storage, registry);

        FileCleanupService.CleanupResult result = service.cleanupExpiredFiles(NOW);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.exhausted()).isEqualTo(1);
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETE_FAILED);
        assertThat(file.getDeleteAttempts()).isEqualTo(10);
        assertThat(registry.get("collectra.file.cleanup.failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("collectra.file.cleanup.exhausted").counter().count())
                .isEqualTo(1.0);
    }

    private FileCleanupService service(
            StoredFileRepository files, ObjectStorage storage, SimpleMeterRegistry registry) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getCleanup().setMaxDeleteAttempts(10);
        return new FileCleanupService(files, storage, properties, noOpTransactions(), registry);
    }

    private StoredFileRepository repositoryFor(StoredFile file) {
        StoredFileRepository files = mock(StoredFileRepository.class);
        when(files.lockCleanupCandidateIds(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(file.getId()), List.of());
        when(files.findAllById(List.of(file.getId()))).thenReturn(List.of(file));
        when(files.findById(file.getId())).thenReturn(Optional.of(file));
        return files;
    }

    private StoredFile readyFile() {
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        StoredFile file =
                new StoredFile(
                        fileId,
                        tenantId,
                        null,
                        FileCategory.TEMP,
                        "rustfs",
                        "collectra-temp",
                        "temp/" + tenantId + "/" + fileId,
                        "sample.tmp",
                        "application/octet-stream",
                        NOW.minusSeconds(60),
                        null);
        file.markReady(4, "application/octet-stream", "a".repeat(64));
        return file;
    }

    private PlatformTransactionManager noOpTransactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };
    }
}
