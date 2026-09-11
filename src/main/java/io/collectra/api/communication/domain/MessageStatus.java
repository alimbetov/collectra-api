package io.collectra.api.communication.domain;

public enum MessageStatus {
    QUEUED,
    PROCESSING,
    RETRY_WAIT,
    SENT,
    FAILED
}
