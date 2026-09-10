package io.collectra.api.shared.outbox;

public class UnknownOutboxEventTypeException extends RuntimeException {
    public UnknownOutboxEventTypeException(String eventType) {
        super("Unsupported outbox event type: " + eventType);
    }
}
