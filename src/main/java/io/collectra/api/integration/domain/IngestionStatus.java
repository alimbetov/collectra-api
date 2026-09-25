package io.collectra.api.integration.domain;

public enum IngestionStatus {
    QUEUED,
    PROCESSING,
    RETRY_WAIT,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED
}
