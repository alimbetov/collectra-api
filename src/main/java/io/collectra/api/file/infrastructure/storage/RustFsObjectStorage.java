package io.collectra.api.file.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class RustFsObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final S3Presigner presigner;

    public RustFsObjectStorage(S3Client client, S3Presigner presigner) {
        this.client = client;
        this.presigner = presigner;
    }

    @Override
    public StoredObject upload(UploadObject command) {
        try {
            var requestBuilder = PutObjectRequest.builder()
                    .bucket(command.location().bucket())
                    .key(command.location().objectKey())
                    .metadata(command.metadata());
            if (command.contentType() != null && !command.contentType().isBlank()) {
                requestBuilder.contentType(command.contentType());
            }
            var response = client.putObject(
                    requestBuilder.build(),
                    RequestBody.fromInputStream(command.content(), command.contentLength()));
            return new StoredObject(command.contentLength(), response.eTag());
        } catch (RuntimeException ex) {
            throw new FileStorageException("Cannot upload object to configured storage", ex);
        }
    }

    @Override
    public InputStream download(StorageLocation location) {
        try {
            return client.getObject(
                    GetObjectRequest.builder()
                            .bucket(location.bucket())
                            .key(location.objectKey())
                            .build());
        } catch (RuntimeException ex) {
            throw new FileStorageException("Cannot download object from configured storage", ex);
        }
    }

    @Override
    public ObjectMetadata stat(StorageLocation location) {
        try {
            var response = client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(location.bucket())
                            .key(location.objectKey())
                            .build());
            return new ObjectMetadata(response.contentLength(), response.contentType(), response.eTag());
        } catch (RuntimeException ex) {
            throw new FileStorageException("Cannot read object metadata from configured storage", ex);
        }
    }

    @Override
    public void delete(StorageLocation location) {
        try {
            client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(location.bucket())
                            .key(location.objectKey())
                            .build());
        } catch (RuntimeException ex) {
            throw new FileStorageException("Cannot delete object from configured storage", ex);
        }
    }

    @Override
    public boolean exists(StorageLocation location) {
        try {
            client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(location.bucket())
                            .key(location.objectKey())
                            .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) return false;
            throw new FileStorageException("Cannot check object existence in configured storage", ex);
        } catch (RuntimeException ex) {
            throw new FileStorageException("Cannot check object existence in configured storage", ex);
        }
    }

    @Override
    public URI generatePresignedGetUrl(StorageLocation location, Duration ttl) {
        try {
            var objectRequest = GetObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.objectKey())
                    .build();
            return presigner.presignGetObject(
                            GetObjectPresignRequest.builder()
                                    .signatureDuration(ttl)
                                    .getObjectRequest(objectRequest)
                                    .build())
                    .url()
                    .toURI();
        } catch (Exception ex) {
            throw new FileStorageException("Cannot generate presigned download URL", ex);
        }
    }

    @Override
    public URI generatePresignedPutUrl(
            StorageLocation location, String contentType, Duration ttl) {
        try {
            var requestBuilder = PutObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.objectKey());
            if (contentType != null && !contentType.isBlank()) requestBuilder.contentType(contentType);
            return presigner.presignPutObject(
                            PutObjectPresignRequest.builder()
                                    .signatureDuration(ttl)
                                    .putObjectRequest(requestBuilder.build())
                                    .build())
                    .url()
                    .toURI();
        } catch (Exception ex) {
            throw new FileStorageException("Cannot generate presigned upload URL", ex);
        }
    }
}
