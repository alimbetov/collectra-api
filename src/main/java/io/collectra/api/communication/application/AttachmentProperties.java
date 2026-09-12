package io.collectra.api.communication.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "collectra.communication.attachments")
public class AttachmentProperties {
    private DataSize maxFileSize = DataSize.ofMegabytes(10);
    private DataSize maxTotalSize = DataSize.ofMegabytes(20);
    private int maxCount = 10;

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = requirePositive(maxFileSize, "maxFileSize");
    }

    public DataSize getMaxTotalSize() {
        return maxTotalSize;
    }

    public void setMaxTotalSize(DataSize maxTotalSize) {
        this.maxTotalSize = requirePositive(maxTotalSize, "maxTotalSize");
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount must be positive");
        }
        this.maxCount = maxCount;
    }

    private DataSize requirePositive(DataSize value, String field) {
        if (value == null || value.toBytes() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
