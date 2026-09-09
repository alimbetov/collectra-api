package io.collectra.api.file.infrastructure.storage;

import io.collectra.api.file.domain.FileCategory;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "collectra.file")
public class FileStorageProperties {
    private DataSize directUploadMaxSize = DataSize.ofMegabytes(50);
    private final Storage storage = new Storage();
    private final Retention retention = new Retention();
    private final Cleanup cleanup = new Cleanup();
    private final PresignedUrl presignedUrl = new PresignedUrl();

    public String bucketFor(FileCategory category) {
        return switch (category) {
            case IMPORT_SOURCE -> storage.buckets.source;
            case REPORT, EXPORT -> storage.buckets.generated;
            case ASSET -> storage.buckets.assets;
            case TEMP -> storage.buckets.temp;
        };
    }

    public DataSize getDirectUploadMaxSize() { return directUploadMaxSize; }
    public void setDirectUploadMaxSize(DataSize value) { this.directUploadMaxSize = value; }
    public Storage getStorage() { return storage; }
    public Retention getRetention() { return retention; }
    public Cleanup getCleanup() { return cleanup; }
    public PresignedUrl getPresignedUrl() { return presignedUrl; }

    public static class Storage {
        private String provider = "rustfs";
        private String endpoint = "http://localhost:9000";
        private String accessKey;
        private String secretKey;
        private String region = "us-east-1";
        private boolean pathStyleAccess = true;
        private final Buckets buckets = new Buckets();

        public String getProvider() { return provider; }
        public void setProvider(String value) { this.provider = value; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String value) { this.endpoint = value; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String value) { this.accessKey = value; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String value) { this.secretKey = value; }
        public String getRegion() { return region; }
        public void setRegion(String value) { this.region = value; }
        public boolean isPathStyleAccess() { return pathStyleAccess; }
        public void setPathStyleAccess(boolean value) { this.pathStyleAccess = value; }
        public Buckets getBuckets() { return buckets; }
    }

    public static class Buckets {
        private String source = "collectra-source";
        private String generated = "collectra-generated";
        private String assets = "collectra-assets";
        private String temp = "collectra-temp";

        public String getSource() { return source; }
        public void setSource(String value) { this.source = value; }
        public String getGenerated() { return generated; }
        public void setGenerated(String value) { this.generated = value; }
        public String getAssets() { return assets; }
        public void setAssets(String value) { this.assets = value; }
        public String getTemp() { return temp; }
        public void setTemp(String value) { this.temp = value; }
    }

    public static class Retention {
        private Duration importSource = Duration.ofDays(90);
        private Duration report = Duration.ofDays(90);
        private Duration export = Duration.ofDays(90);
        private Duration temp = Duration.ofDays(3);

        public Duration getImportSource() { return importSource; }
        public void setImportSource(Duration value) { this.importSource = value; }
        public Duration getReport() { return report; }
        public void setReport(Duration value) { this.report = value; }
        public Duration getExport() { return export; }
        public void setExport(Duration value) { this.export = value; }
        public Duration getTemp() { return temp; }
        public void setTemp(Duration value) { this.temp = value; }
    }

    public static class Cleanup {
        private String cron = "0 0 3 * * *";
        private int batchSize = 500;
        private int maxDeleteAttempts = 10;
        private Duration retryDelay = Duration.ofMinutes(15);

        public String getCron() { return cron; }
        public void setCron(String value) { this.cron = value; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int value) { this.batchSize = value; }
        public int getMaxDeleteAttempts() { return maxDeleteAttempts; }
        public void setMaxDeleteAttempts(int value) { this.maxDeleteAttempts = value; }
        public Duration getRetryDelay() { return retryDelay; }
        public void setRetryDelay(Duration value) { this.retryDelay = value; }
    }

    public static class PresignedUrl {
        private Duration downloadTtl = Duration.ofMinutes(10);
        private Duration uploadTtl = Duration.ofMinutes(30);

        public Duration getDownloadTtl() { return downloadTtl; }
        public void setDownloadTtl(Duration value) { this.downloadTtl = value; }
        public Duration getUploadTtl() { return uploadTtl; }
        public void setUploadTtl(Duration value) { this.uploadTtl = value; }
    }
}
