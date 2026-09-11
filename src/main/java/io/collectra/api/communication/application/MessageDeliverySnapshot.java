package io.collectra.api.communication.application;

import io.collectra.api.communication.domain.CommunicationChannel;
import java.util.UUID;

public record MessageDeliverySnapshot(
        UUID messageId,
        UUID tenantId,
        UUID campaignRunId,
        CommunicationChannel channel,
        String destination,
        String subject,
        String body,
        int attemptCount) {}
