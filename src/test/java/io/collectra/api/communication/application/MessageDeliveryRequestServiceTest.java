package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageDeliveryRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    private final MessageAttachmentRepository attachments = mock(MessageAttachmentRepository.class);
    private final MessageDeliveryEventPublisher events = mock(MessageDeliveryEventPublisher.class);
    private final MessageDeliveryRequestService service =
            new MessageDeliveryRequestService(
                    attachments, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void eligibleMessageGetsDurableMarkerAndEvent() {
        Message message = queued();
        when(attachments.existsRequiredNotReady(
                        message.getTenantId(), message.getId(), MessageAttachmentStatus.READY))
                .thenReturn(false);

        assertThat(service.requestIfEligible(message)).isTrue();

        assertThat(message.getDeliveryRequestedAt()).isEqualTo(NOW);
        verify(events).requestDelivery(message.getTenantId(), message.getId());
    }

    @Test
    void duplicateEligibilityCheckDoesNotPublishSecondEvent() {
        Message message = queued();
        when(attachments.existsRequiredNotReady(
                        message.getTenantId(), message.getId(), MessageAttachmentStatus.READY))
                .thenReturn(false);

        assertThat(service.requestIfEligible(message)).isTrue();
        assertThat(service.requestIfEligible(message)).isFalse();

        verify(events).requestDelivery(message.getTenantId(), message.getId());
    }

    @Test
    void requiredNotReadyAttachmentBlocksMarkerAndEvent() {
        Message message = queued();
        when(attachments.existsRequiredNotReady(
                        message.getTenantId(), message.getId(), MessageAttachmentStatus.READY))
                .thenReturn(true);

        assertThat(service.requestIfEligible(message)).isFalse();

        assertThat(message.getDeliveryRequestedAt()).isNull();
        verify(events, never()).requestDelivery(message.getTenantId(), message.getId());
    }

    @Test
    void nonQueuedMessageCannotPublishDeliveryRequest() {
        Message message = queued();
        message.beginAttempt(NOW.minusSeconds(1));

        assertThat(service.requestIfEligible(message)).isFalse();

        verify(attachments, never())
                .existsRequiredNotReady(
                        message.getTenantId(), message.getId(), MessageAttachmentStatus.READY);
        verify(events, never()).requestDelivery(message.getTenantId(), message.getId());
    }

    private Message queued() {
        return Message.queued(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommunicationChannel.EMAIL,
                "client@example.com",
                "ru",
                "Subject",
                "<p>Body</p>");
    }
}
