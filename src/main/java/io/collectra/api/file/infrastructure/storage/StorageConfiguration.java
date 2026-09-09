package io.collectra.api.file.infrastructure.storage;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class StorageConfiguration {

    @Bean
    S3Client s3Client(FileStorageProperties properties) {
        var storage = properties.getStorage();
        var credentials = credentials(storage);
        return S3Client.builder()
                .endpointOverride(URI.create(storage.getEndpoint()))
                .credentialsProvider(credentials)
                .region(Region.of(storage.getRegion()))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(storage.isPathStyleAccess())
                                .build())
                .build();
    }

    @Bean
    S3Presigner s3Presigner(FileStorageProperties properties) {
        var storage = properties.getStorage();
        return S3Presigner.builder()
                .endpointOverride(URI.create(storage.getEndpoint()))
                .credentialsProvider(credentials(storage))
                .region(Region.of(storage.getRegion()))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(storage.isPathStyleAccess())
                                .build())
                .build();
    }

    private StaticCredentialsProvider credentials(FileStorageProperties.Storage storage) {
        if (storage.getAccessKey() == null || storage.getAccessKey().isBlank()) {
            throw new IllegalStateException("collectra.file.storage.access-key must be configured");
        }
        if (storage.getSecretKey() == null || storage.getSecretKey().isBlank()) {
            throw new IllegalStateException("collectra.file.storage.secret-key must be configured");
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey()));
    }
}
