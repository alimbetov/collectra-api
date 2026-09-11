package io.collectra.api.communication.application;

import io.collectra.api.communication.domain.CommunicationChannel;
import java.util.UUID;

public record DeliveryCommand(
        UUID messageId,
        UUID tenantId,
        CommunicationChannel channel,
        String destination,
        String subject,
        String body) {}
