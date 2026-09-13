package io.collectra.api.communication.application;

import java.util.Objects;

public sealed interface DeliveryResult {
    record Accepted(String providerMessageId) implements DeliveryResult {}

    record Rejected(DeliveryFailureKind kind, String code, String message)
            implements DeliveryResult {
        public Rejected {
            Objects.requireNonNull(kind, "kind is required");
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code is required");
            }
            code = code.trim();
        }
    }

    record Unknown(String code, String message) implements DeliveryResult {
        public Unknown {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code is required");
            }
            code = code.trim();
        }
    }
}
