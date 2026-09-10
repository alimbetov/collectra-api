package io.collectra.api.shared.outbox;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    PUBLISHED,
    DEAD
}
