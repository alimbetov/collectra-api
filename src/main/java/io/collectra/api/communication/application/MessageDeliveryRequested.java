package io.collectra.api.communication.application;

import java.util.Objects;
import java.util.UUID;

public record MessageDeliveryRequested(UUID tenantId, UUID messageId) {
    public static final String AGGREGATE_TYPE = "MESSAGE";
    public static final String EVENT_TYPE = "MESSAGE_DELIVERY_REQUESTED";

    public MessageDeliveryRequested {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(messageId, "messageId");
    }
}
