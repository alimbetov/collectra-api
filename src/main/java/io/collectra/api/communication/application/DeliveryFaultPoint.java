package io.collectra.api.communication.application;

public enum DeliveryFaultPoint {
    AFTER_CLAIM,
    BEFORE_PROVIDER,
    AFTER_PROVIDER_ACCEPTED,
    BEFORE_STATE_COMMIT,
    AFTER_STATE_COMMIT
}
