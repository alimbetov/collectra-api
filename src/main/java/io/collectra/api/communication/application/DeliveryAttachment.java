package io.collectra.api.communication.application;

import java.util.Arrays;
import java.util.Objects;

public record DeliveryAttachment(String filename, String contentType, byte[] content) {
    public DeliveryAttachment {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType is required");
        }
        content =
                Arrays.copyOf(
                        Objects.requireNonNull(content, "content is required"), content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
