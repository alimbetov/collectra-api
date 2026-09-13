package io.collectra.api.communication.domain;

public enum DeliveryAttemptStatus {
    STARTED,
    ACCEPTED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    UNKNOWN
}
