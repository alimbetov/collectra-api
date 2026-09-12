package io.collectra.api.communication.application;

import io.collectra.api.communication.domain.CommunicationChannel;
import java.util.List;
import java.util.UUID;

public record DeliveryCommand(
        UUID messageId,
        UUID tenantId,
        String deliveryKey,
        int attemptNo,
        CommunicationChannel channel,
        String destination,
        String subject,
        String body,
        List<DeliveryAttachment> attachments) {

    public DeliveryCommand {
        if (deliveryKey == null || deliveryKey.isBlank()) {
            throw new IllegalArgumentException("deliveryKey is required");
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
