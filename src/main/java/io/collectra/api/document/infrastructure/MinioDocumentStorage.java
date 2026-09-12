package io.collectra.api.document.infrastructure;

import io.collectra.api.document.application.DocumentObjectNotFoundException;
import io.collectra.api.document.application.DocumentStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioDocumentStorage implements DocumentStorage {
    private final MinioClient client;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    public MinioDocumentStorage(
            @Value("${collectra.storage.endpoint}") String endpoint,
            @Value("${collectra.storage.access-key}") String accessKey,
            @Value("${collectra.storage.secret-key}") String secretKey,
            @Value("${collectra.storage.bucket}") String bucket) {
        this.client =
                MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    @Override
    public StoredObject put(String key, byte[] content, String mediaType) {
        try {
            ensureBucket();
            client.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(key).stream(
                                    new ByteArrayInputStream(content), content.length, -1)
                            .contentType(mediaType)
                            .build());
            return new StoredObject(key, mediaType, content.length, sha256(content));
        } catch (Exception ex) {
            throw new DocumentStorageException("Cannot store generated document", ex);
        }
    }

    @Override
    public byte[] get(String key) {
        try (var input =
                client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return input.readAllBytes();
        } catch (ErrorResponseException ex) {
            String code = ex.errorResponse() == null ? null : ex.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                throw new DocumentObjectNotFoundException(key, ex);
            }
            throw new DocumentStorageException("Cannot read generated document", ex);
        } catch (Exception ex) {
            throw new DocumentStorageException("Cannot read generated document", ex);
        }
    }

    private synchronized void ensureBucket() throws Exception {
        if (bucketReady.get()) {
            return;
        }
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketReady.set(true);
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    public static class DocumentStorageException extends RuntimeException {
        public DocumentStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
