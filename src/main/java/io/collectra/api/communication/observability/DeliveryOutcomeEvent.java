package io.collectra.api.communication.observability;

import io.collectra.api.communication.domain.CommunicationChannel;
import java.time.Instant;
import java.util.UUID;

public record DeliveryOutcomeEvent(
        UUID tenantId,
        UUID campaignId,
        UUID campaignRunId,
        UUID messageId,
        CommunicationChannel channel,
        int attemptCount,
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
