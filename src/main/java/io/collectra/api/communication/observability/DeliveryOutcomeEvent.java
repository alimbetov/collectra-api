package io.collectra.api.communication.observability;

import io.collectra.api.communication.domain.CommunicationChannel;
import java.time.Instant;

public record DeliveryOutcomeEvent(
        CommunicationChannel channel,
        Outcome outcome,
        String errorCode,
        Instant messageCreatedAt,
        Instant outcomeAt) {
    public enum Outcome {
        SENT,
        FAILED,
        RETRY_SCHEDULED
    }
}
