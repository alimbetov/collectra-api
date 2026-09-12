package io.collectra.api.communication.application;

import io.collectra.api.communication.domain.CommunicationChannel;
import java.util.List;
import java.util.UUID;

public record DeliveryCommand(
        UUID messageId,
        UUID tenantId,
        CommunicationChannel channel,
        String destination,
        String subject,
        String body,
        List<DeliveryAttachment> attachments) {

    public DeliveryCommand {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public DeliveryCommand(
            UUID messageId,
            UUID tenantId,
            CommunicationChannel channel,
            String destination,
            String subject,
            String body) {
        this(messageId, tenantId, channel, destination, subject, body, List.of());
    }
}
