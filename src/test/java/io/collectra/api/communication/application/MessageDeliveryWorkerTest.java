package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.communication.domain.CommunicationChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryWorkerTest {
    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");
    private static final String DELIVERY_KEY = "msg-test-delivery-key";

    @Mock MessageStateService states;
    @Mock MessageAttachmentContentResolver attachmentContent;
    @Mock DeliveryGateway gateway;

    private MessageDeliveryWorker worker;
    private UUID tenantId;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        messageId = UUID.randomUUID();
        worker =
                new MessageDeliveryWorker(
                        states,
                        attachmentContent,
                        gateway,
                        new MessageRetryPolicy(),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptedDeliveryMarksMessageSent() {
        claim(1);
        when(gateway.deliver(any())).thenReturn(new DeliveryResult.Accepted("kumo-1"));

        worker.deliver(tenantId, messageId);

        verify(states).markSent(tenantId, messageId, "kumo-1");
    }

    @Test
    void ambiguousDeliveryMarksUnknownAndDoesNotScheduleRetry() {
        claim(1);
        when(gateway.deliver(any()))
                .thenReturn(new DeliveryResult.Unknown("PROVIDER_TIMEOUT", "response lost"));

        worker.deliver(tenantId, messageId);

        verify(states).markUnknown(tenantId, messageId, "PROVIDER_TIMEOUT", "response lost");
        verify(states, never()).scheduleRetry(any(), any(), any(), any(), any());
    }

    @Test
    void retryableFailureSchedulesDeterministicRetry() {
        claim(2);
        when(gateway.deliver(any()))
                .thenReturn(
                        new DeliveryResult.Rejected(
                                DeliveryFailureKind.RETRYABLE, "PROVIDER_BUSY", "try later"));

        worker.deliver(tenantId, messageId);

        verify(states)
                .scheduleRetry(
                        tenantId, messageId, NOW.plusSeconds(600), "PROVIDER_BUSY", "try later");
    }

    @Test
    void permanentFailureMarksMessageFailed() {
        claim(1);
        when(gateway.deliver(any()))
                .thenReturn(
                        new DeliveryResult.Rejected(
                                DeliveryFailureKind.PERMANENT, "INVALID_DESTINATION", "invalid"));

        worker.deliver(tenantId, messageId);

        verify(states).markFailed(tenantId, messageId, "INVALID_DESTINATION", "invalid");
    }

    @Test
    void exhaustedRetryableFailureMarksMessageFailed() {
        claim(MessageRetryPolicy.MAX_ATTEMPTS);
        when(gateway.deliver(any()))
                .thenReturn(
                        new DeliveryResult.Rejected(
                                DeliveryFailureKind.RETRYABLE,
                                "PROVIDER_BUSY",
                                "still unavailable"));

        worker.deliver(tenantId, messageId);

        verify(states).markFailed(tenantId, messageId, "PROVIDER_BUSY", "still unavailable");
        verify(states, never()).scheduleRetry(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateOrTerminalMessageDoesNotResolveAttachmentsOrCallProvider() {
        when(states.begin(tenantId, messageId)).thenReturn(Optional.empty());

        worker.deliver(tenantId, messageId);

        verify(attachmentContent, never()).resolve(any(), any());
        verify(states, never()).beginProviderAttempt(any(), any());
        verify(gateway, never()).deliver(any());
    }

    @Test
    void sendsResolvedAttachmentsAndStableAttemptIdentityToGateway() {
        claim(1);
        DeliveryAttachment attachment =
                new DeliveryAttachment("invoice.pdf", "application/pdf", new byte[] {1, 2, 3});
        when(attachmentContent.resolve(tenantId, messageId)).thenReturn(List.of(attachment));
        when(gateway.deliver(any())).thenReturn(new DeliveryResult.Accepted(null));
        ArgumentCaptor<DeliveryCommand> command = ArgumentCaptor.forClass(DeliveryCommand.class);

        worker.deliver(tenantId, messageId);

        verify(gateway).deliver(command.capture());
        assertThat(command.getValue().attachments()).containsExactly(attachment);
        assertThat(command.getValue().deliveryKey()).isEqualTo(DELIVERY_KEY);
        assertThat(command.getValue().attemptNo()).isOne();
    }

    @Test
    void retryableAttachmentReadFailureSchedulesRetryWithoutProviderAttempt() {
        claimWithoutAttachmentsStub(2);
        when(attachmentContent.resolve(tenantId, messageId))
                .thenThrow(
                        new AttachmentResolutionException(
                                "ATTACHMENT_STORAGE_READ_FAILED",
                                DeliveryFailureKind.RETRYABLE,
                                "Temporary attachment storage read failure"));

        worker.deliver(tenantId, messageId);

        verify(states)
                .scheduleRetry(
                        tenantId,
                        messageId,
                        NOW.plusSeconds(600),
                        "ATTACHMENT_STORAGE_READ_FAILED",
                        "Temporary attachment storage read failure");
        verify(states, never()).beginProviderAttempt(any(), any());
        verify(gateway, never()).deliver(any());
    }

    @Test
    void permanentAttachmentFailureMarksFailedWithoutCallingProvider() {
        claimWithoutAttachmentsStub(1);
        when(attachmentContent.resolve(tenantId, messageId))
                .thenThrow(
                        new AttachmentResolutionException(
                                "ATTACHMENT_OBJECT_MISSING",
                                DeliveryFailureKind.PERMANENT,
                                "Attachment object is missing"));

        worker.deliver(tenantId, messageId);

        verify(states)
                .markFailed(
                        tenantId,
                        messageId,
                        "ATTACHMENT_OBJECT_MISSING",
                        "Attachment object is missing");
        verify(states, never()).beginProviderAttempt(any(), any());
        verify(gateway, never()).deliver(any());
    }

    @Test
    void sendsOnlyImmutableSnapshotToGateway() {
        claim(1);
        when(gateway.deliver(any())).thenReturn(new DeliveryResult.Accepted(null));
        ArgumentCaptor<DeliveryCommand> command = ArgumentCaptor.forClass(DeliveryCommand.class);

        worker.deliver(tenantId, messageId);

        verify(gateway).deliver(command.capture());
        assertThat(command.getValue())
                .isEqualTo(
                        new DeliveryCommand(
                                messageId,
                                tenantId,
                                DELIVERY_KEY,
                                1,
                                CommunicationChannel.EMAIL,
                                "client@example.com",
                                "Subject",
                                "<p>Body</p>",
                                List.of()));
    }

    private void claim(int providerAttemptNo) {
        claimWithoutAttachmentsStub(providerAttemptNo);
        when(states.beginProviderAttempt(tenantId, messageId))
                .thenReturn(Optional.of(new ProviderAttemptSnapshot(DELIVERY_KEY, providerAttemptNo)));
        when(attachmentContent.resolve(tenantId, messageId)).thenReturn(List.of());
    }

    private void claimWithoutAttachmentsStub(int processingAttemptNo) {
        when(states.begin(tenantId, messageId))
                .thenReturn(
                        Optional.of(
                                new MessageDeliverySnapshot(
                                        messageId,
                                        tenantId,
                                        UUID.randomUUID(),
                                        CommunicationChannel.EMAIL,
                                        "client@example.com",
                                        "Subject",
                                        "<p>Body</p>",
                                        Math.max(0, processingAttemptNo - 1),
                                        processingAttemptNo)));
    }
}
