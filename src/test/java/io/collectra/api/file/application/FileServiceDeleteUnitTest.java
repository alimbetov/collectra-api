package io.collectra.api.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import io.collectra.api.file.infrastructure.persistence.StoredFileRepository;
import io.collectra.api.file.infrastructure.storage.FileStorageException;
import io.collectra.api.file.infrastructure.storage.FileStorageProperties;
import io.collectra.api.file.infrastructure.storage.ObjectStorage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class FileServiceDeleteUnitTest {
    @Test
    void manualDeleteStopsCallingStorageAfterMaxAttempts() {
        UUID tenantId = UUID.randomUUID();
        StoredFile file = readyFile(tenantId);
        StoredFileRepository files = mock(StoredFileRepository.class);
        when(files.findByIdAndTenantId(file.getId(), tenantId)).thenReturn(Optional.of(file));
        when(files.saveAndFlush(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(files.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectStorage storage = mock(ObjectStorage.class);
        doThrow(new FileStorageException("persistent failure", new RuntimeException("down")))
                .when(storage)
                .delete(any());

        FileStorageProperties properties = new FileStorageProperties();
        properties.getCleanup().setMaxDeleteAttempts(10);
        FileService service = new FileService(
                files,
                storage,
                mock(FileKeyGenerator.class),
                mock(FileRetentionPolicy.class),
                properties,
                noOpTransactions());

        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThatThrownBy(() -> service.delete(tenantId, file.getId()))
                    .isInstanceOf(FileStorageException.class);
            assertThat(file.getDeleteAttempts()).isEqualTo(attempt);
        }

        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETE_FAILED);
        assertThatThrownBy(() -> service.delete(tenantId, file.getId()))
                .isInstanceOf(FileNotReadyException.class);
        verify(storage, times(10)).delete(any());
    }

    @Test
    void firstManualDeleteFailureRemainsRetryable() {
        UUID tenantId = UUID.randomUUID();
        StoredFile file = readyFile(tenantId);
        StoredFileRepository files = mock(StoredFileRepository.class);
        when(files.findByIdAndTenantId(file.getId(), tenantId)).thenReturn(Optional.of(file));
        when(files.saveAndFlush(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(files.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectStorage storage = mock(ObjectStorage.class);
        doThrow(new FileStorageException("temporary failure", new RuntimeException("down")))
                .when(storage)
                .delete(any());

        FileStorageProperties properties = new FileStorageProperties();
        properties.getCleanup().setMaxDeleteAttempts(3);
        FileService service = new FileService(
                files,
                storage,
                mock(FileKeyGenerator.class),
                mock(FileRetentionPolicy.class),
                properties,
                noOpTransactions());

        assertThatThrownBy(() -> service.delete(tenantId, file.getId()))
                .isInstanceOf(FileStorageException.class);

        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETE_PENDING);
        assertThat(file.getDeleteAttempts()).isEqualTo(1);
        assertThat(file.getLastError()).contains("temporary failure");
    }

    private StoredFile readyFile(UUID tenantId) {
        UUID fileId = UUID.randomUUID();
        StoredFile file = new StoredFile(
                fileId,
                tenantId,
                null,
                FileCategory.TEMP,
                "rustfs",
                "collectra-temp",
                "temp/" + tenantId + "/" + fileId,
                "sample.tmp",
                "application/octet-stream",
                Instant.parse("2026-09-10T10:00:00Z"),
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
