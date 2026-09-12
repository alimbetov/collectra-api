package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MessageStateServiceAttachmentGateTest {
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    private final MessageRepository messages = mock(MessageRepository.class);
    private final MessageAttachmentRepository attachments = mock(MessageAttachmentRepository.class);
    private final CampaignRunRepository runs = mock(CampaignRunRepository.class);
    private final MessageRetryPolicy retryPolicy = new MessageRetryPolicy();
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final MessageStateService service =
            new MessageStateService(
                    messages,
                    attachments,
                    runs,
                    retryPolicy,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    events);

    @Test
    void requiredPendingAttachmentBlocksQueuedToProcessing() {
        Message message = queued();
        when(messages.findLockedByIdAndTenantId(message.getTenantId(), message.getId()))
                .thenReturn(Optional.of(message));
        when(attachments.existsRequiredNotReady(
                        message.getTenantId(), message.getId(), MessageAttachmentStatus.READY))
                .thenReturn(true);

        assertThat(service.begin(message.getTenantId(), message.getId())).isEmpty();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(message.getAttemptCount()).isZero();
    }

    @Test
    void allRequiredReadyAllowsProcessing() {
        Message message = queued();
        when(messages.findLockedByIdAndTenantId(message.getTenantId(), message.getId()))
                .thenReturn(Optional.of(message));
        when(attachments.existsRequiredNotReady(
                        message.getTenantId(), message.getId(), MessageAttachmentStatus.READY))
                .thenReturn(false);

        Optional<MessageDeliverySnapshot> result =
                service.begin(message.getTenantId(), message.getId());

        assertThat(result).isPresent();
        assertThat(message.getStatus()).isEqualTo(MessageStatus.PROCESSING);
        assertThat(message.getAttemptCount()).isEqualTo(1);
        assertThat(message.getProcessingStartedAt()).isEqualTo(NOW);
    }

    @Test
    void terminalOrDuplicateMessageDoesNotEvaluateAttachmentReadiness() {
        Message message = queued();
        message.beginAttempt(NOW.minusSeconds(10));
        when(messages.findLockedByIdAndTenantId(message.getTenantId(), message.getId()))
                .thenReturn(Optional.of(message));

        assertThat(service.begin(message.getTenantId(), message.getId())).isEmpty();

        verify(attachments, never())
                .existsRequiredNotReady(
                        message.getTenantId(), message.getId(), MessageAttachmentStatus.READY);
    }

    @Test
    void readinessLookupIsTenantScoped() {
        Message message = queued();
        UUID tenantId = message.getTenantId();
        when(messages.findLockedByIdAndTenantId(tenantId, message.getId()))
                .thenReturn(Optional.of(message));
        when(attachments.existsRequiredNotReady(
                        tenantId, message.getId(), MessageAttachmentStatus.READY))
                .thenReturn(false);

        service.begin(tenantId, message.getId());

        verify(attachments)
                .existsRequiredNotReady(tenantId, message.getId(), MessageAttachmentStatus.READY);
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
