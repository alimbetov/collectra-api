package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import io.collectra.api.file.infrastructure.persistence.StoredFileRepository;
import io.collectra.api.file.infrastructure.storage.FileStorageException;
import io.collectra.api.file.infrastructure.storage.FileStorageProperties;
import io.collectra.api.file.infrastructure.storage.ObjectStorage;
import io.collectra.api.file.infrastructure.storage.StorageLocation;
import io.collectra.api.file.infrastructure.storage.UploadObject;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FileService {
    private final StoredFileRepository files;
    private final ObjectStorage storage;
    private final FileKeyGenerator keyGenerator;
    private final FileRetentionPolicy retentionPolicy;
    private final FileStorageProperties properties;
    private final TransactionTemplate transactions;

    public FileService(
            StoredFileRepository files,
            ObjectStorage storage,
            FileKeyGenerator keyGenerator,
            FileRetentionPolicy retentionPolicy,
            FileStorageProperties properties,
            PlatformTransactionManager transactionManager) {
        this.files = files;
        this.storage = storage;
        this.keyGenerator = keyGenerator;
        this.retentionPolicy = retentionPolicy;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public FileMetadata upload(UploadFileCommand command) {
        long maxBytes = properties.getDirectUploadMaxSize().toBytes();
        if (command.contentLength() > maxBytes) {
            throw new FileTooLargeException(command.contentLength(), maxBytes);
        }

        UUID fileId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String bucket = properties.bucketFor(command.category());
        String objectKey = keyGenerator.generate(
                command.category(), command.tenantId(), command.projectId(), fileId, createdAt);
        var location = new StorageLocation(bucket, objectKey);
        var entity = new StoredFile(
                fileId,
                command.tenantId(),
                command.projectId(),
                command.category(),
                properties.getStorage().getProvider(),
                bucket,
                objectKey,
                command.originalFilename(),
                command.contentType(),
                retentionPolicy.expiresAt(command.category(), createdAt),
                command.createdBy());

        transactions.executeWithoutResult(status -> files.saveAndFlush(entity));

        MessageDigest digest = sha256();
        try (DigestInputStream input = new DigestInputStream(command.content(), digest)) {
            var stored = storage.upload(new UploadObject(
                    location,
                    input,
                    command.contentLength(),
                    command.contentType(),
                    Map.of("file-id", fileId.toString())));
            if (stored.sizeBytes() != command.contentLength()) {
                throw new FileStorageException(
                        "Storage reported a size different from the requested content length",
                        new IllegalStateException("object size mismatch"));
            }
        } catch (Exception ex) {
            markUploadFailed(command.tenantId(), fileId, ex);
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new FileStorageException("Cannot upload file", ex);
        }

        String checksum = HexFormat.of().formatHex(digest.digest());
        return transactions.execute(status -> {
            StoredFile current = requireFile(command.tenantId(), fileId);
            current.markReady(command.contentLength(), command.contentType(), checksum);
            return FileMetadata.from(files.save(current));
        });
    }

    public FileMetadata get(UUID tenantId, UUID fileId) {
        return FileMetadata.from(requireFile(tenantId, fileId));
    }

    public FileDownload openContent(UUID tenantId, UUID fileId) {
        StoredFile file = requireReadyFile(tenantId, fileId);
        return new FileDownload(FileMetadata.from(file), storage.download(location(file)));
    }

    public PresignedDownload generateDownloadUrl(UUID tenantId, UUID fileId) {
        StoredFile file = requireReadyFile(tenantId, fileId);
        var ttl = properties.getPresignedUrl().getDownloadTtl();
        return new PresignedDownload(fileId, storage.generatePresignedGetUrl(location(file), ttl), ttl);
    }

    public void delete(UUID tenantId, UUID fileId) {
        Instant claimedAt = Instant.now();
        StoredFile claimed = transactions.execute(status -> {
            StoredFile current = requireFile(tenantId, fileId);
            if (current.getStatus() == FileStatus.DELETED) return current;
            if (current.getStatus() != FileStatus.READY
                    && current.getStatus() != FileStatus.DELETE_PENDING) {
                throw new FileNotReadyException(fileId, current.getStatus());
            }
            current.claimDeleteAttempt(claimedAt);
            return files.saveAndFlush(current);
        });

        if (claimed == null || claimed.getStatus() == FileStatus.DELETED) return;

        try {
            storage.delete(location(claimed));
            transactions.executeWithoutResult(status -> {
                StoredFile current = requireFile(tenantId, fileId);
                if (current.getStatus() == FileStatus.DELETED) return;
                current.markDeleted(Instant.now());
                files.save(current);
            });
        } catch (RuntimeException ex) {
            transactions.executeWithoutResult(status -> {
                StoredFile current = requireFile(tenantId, fileId);
                if (current.getStatus() == FileStatus.DELETE_PENDING) {
                    current.registerDeleteFailure(
                            Instant.now(), ex.getMessage(), properties.getCleanup().getMaxDeleteAttempts());
                    files.save(current);
                }
            });
            throw ex;
        }
    }

    private void markUploadFailed(UUID tenantId, UUID fileId, Exception failure) {
        transactions.executeWithoutResult(status -> {
            StoredFile current = requireFile(tenantId, fileId);
            if (current.getStatus() == FileStatus.UPLOADING) {
                current.markFailed(failure.getMessage());
                files.save(current);
            }
        });
    }

    private StoredFile requireReadyFile(UUID tenantId, UUID fileId) {
        StoredFile file = requireFile(tenantId, fileId);
        if (file.getStatus() != FileStatus.READY) {
            throw new FileNotReadyException(fileId, file.getStatus());
        }
        return file;
    }

    private StoredFile requireFile(UUID tenantId, UUID fileId) {
        if (tenantId == null || fileId == null) {
            throw new IllegalArgumentException("tenantId and fileId are required");
        }
        return files.findByIdAndTenantId(fileId, tenantId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    private StorageLocation location(StoredFile file) {
        return new StorageLocation(file.getBucket(), file.getObjectKey());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
