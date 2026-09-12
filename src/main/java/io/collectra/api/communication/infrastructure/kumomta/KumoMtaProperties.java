package io.collectra.api.communication.infrastructure.kumomta;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "collectra.communication.kumomta")
public class KumoMtaProperties {
    private String baseUrl;
    private String envelopeSender;
    private String fromHeader;
    private String username;
    private String password;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(10);

    void validate() {
        requireText(baseUrl, "collectra.communication.kumomta.base-url");
        requireText(envelopeSender, "collectra.communication.kumomta.envelope-sender");
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "collectra.communication.kumomta.base-url is invalid", exception);
        }
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(
                    "collectra.communication.kumomta.base-url must be an absolute HTTP(S) URL");
        }
        requirePositive(connectTimeout, "collectra.communication.kumomta.connect-timeout");
        requirePositive(readTimeout, "collectra.communication.kumomta.read-timeout");
        if (hasText(username) != hasText(password)) {
            throw new IllegalArgumentException(
                    "collectra.communication.kumomta.username and password must be configured"
                            + " together");
        }
    }

    String effectiveFromHeader() {
        return hasText(fromHeader) ? fromHeader.trim() : envelopeSender.trim();
    }

    boolean hasBasicAuth() {
        return hasText(username);
    }

    private static void requireText(String value, String property) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(property + " is required for kumomta provider");
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEnvelopeSender() {
        return envelopeSender;
    }

    public void setEnvelopeSender(String envelopeSender) {
        this.envelopeSender = envelopeSender;
    }

    public String getFromHeader() {
        return fromHeader;
    }

    public void setFromHeader(String fromHeader) {
        this.fromHeader = fromHeader;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
