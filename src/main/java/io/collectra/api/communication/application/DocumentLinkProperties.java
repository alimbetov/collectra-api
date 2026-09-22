package io.collectra.api.communication.application;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "collectra.communication.document-links")
public class DocumentLinkProperties {
    private URI publicBaseUrl = URI.create("http://localhost:8080");
    private Duration ttl = Duration.ofDays(7);
    private long maxDownloadBytes = 20L * 1024 * 1024;
    private int downloadRateLimit = 60;
    private Duration downloadRateWindow = Duration.ofMinutes(1);

    public URI getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(URI publicBaseUrl) {
        this.publicBaseUrl = java.util.Objects.requireNonNull(publicBaseUrl);
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttl = ttl;
    }

    public long getMaxDownloadBytes() {
        return maxDownloadBytes;
    }

    public void setMaxDownloadBytes(long maxDownloadBytes) {
        if (maxDownloadBytes <= 0) {
            throw new IllegalArgumentException("max-download-bytes must be positive");
        }
        this.maxDownloadBytes = maxDownloadBytes;
    }

    public int getDownloadRateLimit() {
        return downloadRateLimit;
    }

    public void setDownloadRateLimit(int downloadRateLimit) {
        if (downloadRateLimit <= 0) {
            throw new IllegalArgumentException("download-rate-limit must be positive");
        }
        this.downloadRateLimit = downloadRateLimit;
    }

    public Duration getDownloadRateWindow() {
        return downloadRateWindow;
    }

    public void setDownloadRateWindow(Duration downloadRateWindow) {
        if (downloadRateWindow == null
                || downloadRateWindow.isZero()
                || downloadRateWindow.isNegative()) {
            throw new IllegalArgumentException("download-rate-window must be positive");
        }
        this.downloadRateWindow = downloadRateWindow;
    }
}
