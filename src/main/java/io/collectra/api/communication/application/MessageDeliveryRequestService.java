package io.collectra.api.communication.application;

import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageDeliveryRequestService {
    private final MessageAttachmentRepository attachments;
    private final MessageDeliveryEventPublisher events;
    private final Clock clock;

    public MessageDeliveryRequestService(
            MessageAttachmentRepository attachments,
            MessageDeliveryEventPublisher events,
            Clock clock) {
        this.attachments = attachments;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean requestIfEligible(Message message) {
        if (message.getStatus() != MessageStatus.QUEUED) {
            return false;
        }
        if (attachments.existsRequiredNotReady(
                message.getTenantId(), message.getId(), MessageAttachmentStatus.READY)) {
            return false;
        }
        if (!message.markDeliveryRequested(clock.instant())) {
            return false;
        }
        events.requestDelivery(message.getTenantId(), message.getId());
        return true;
    }
}
