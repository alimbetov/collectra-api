package io.collectra.api.communication.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.communication.domain.CommunicationChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

    @Mock MessageStateService states;
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
    void duplicateOrTerminalMessageDoesNotCallProvider() {
        when(states.begin(tenantId, messageId)).thenReturn(Optional.empty());

        worker.deliver(tenantId, messageId);

        verify(gateway, never()).deliver(any());
    }

    @Test
    void sendsOnlyImmutableSnapshotToGateway() {
        claim(1);
        when(gateway.deliver(any())).thenReturn(new DeliveryResult.Accepted(null));
        ArgumentCaptor<DeliveryCommand> command = ArgumentCaptor.forClass(DeliveryCommand.class);

        worker.deliver(tenantId, messageId);

        verify(gateway).deliver(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue())
                .isEqualTo(
                        new DeliveryCommand(
                                messageId,
                                tenantId,
                                CommunicationChannel.EMAIL,
                                "client@example.com",
                                "Subject",
                                "<p>Body</p>"));
    }

    private void claim(int attemptCount) {
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
                                        attemptCount)));
    }
}
