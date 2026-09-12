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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryWorkerScenarioMatrixTest {
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
    private static final String DELIVERY_KEY = "msg-worker-matrix";

    @Mock MessageStateService states;
    @Mock MessageAttachmentContentResolver attachments;
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
                        attachments,
                        gateway,
                        new MessageRetryPolicy(),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest(name = "{0} traverses the real worker boundary")
    @EnumSource(CommunicationChannel.class)
    void everyChannelReachesProviderBoundaryWithoutAdapterSpecificAssumptions(
            CommunicationChannel channel) {
        String subject = channel == CommunicationChannel.EMAIL ? "Reminder" : null;
        when(states.begin(tenantId, messageId))
                .thenReturn(Optional.of(snapshot(channel, 1)));
        when(states.beginProviderAttempt(tenantId, messageId))
                .thenReturn(Optional.of(new ProviderAttemptSnapshot(DELIVERY_KEY, 1)));
        when(attachments.resolve(tenantId, messageId)).thenReturn(List.of());
        when(gateway.deliver(any())).thenReturn(new DeliveryResult.Accepted("provider-id"));
        ArgumentCaptor<DeliveryCommand> command = ArgumentCaptor.forClass(DeliveryCommand.class);

        worker.deliver(tenantId, messageId);

        verify(gateway).deliver(command.capture());
        assertThat(command.getValue().channel()).isEqualTo(channel);
        assertThat(command.getValue().destination()).isEqualTo(destination(channel));
        assertThat(command.getValue().subject()).isEqualTo(subject);
        assertThat(command.getValue().body()).isEqualTo("body");
        assertThat(command.getValue().deliveryKey()).isEqualTo(DELIVERY_KEY);
        assertThat(command.getValue().attemptNo()).isOne();
        verify(states).markSent(tenantId, messageId, "provider-id");
    }

    @ParameterizedTest(name = "provider outcome {0} is mapped to {1}")
    @MethodSource("providerOutcomes")
    void providerOutcomeMatrix(
            String name, DeliveryResult result, ExpectedTransition expectedTransition) {
        claim(2);
        when(gateway.deliver(any())).thenReturn(result);

        worker.deliver(tenantId, messageId);

        switch (expectedTransition) {
            case SENT -> verify(states).markSent(tenantId, messageId, "provider-id");
            case UNKNOWN ->
                    verify(states)
                            .markUnknown(
                                    tenantId,
                                    messageId,
                                    ((DeliveryResult.Unknown) result).code(),
                                    ((DeliveryResult.Unknown) result).message());
            case RETRY ->
                    verify(states)
                            .scheduleRetry(
                                    tenantId,
                                    messageId,
                                    NOW.plusSeconds(600),
                                    ((DeliveryResult.Rejected) result).code(),
                                    ((DeliveryResult.Rejected) result).message());
            case FAILED ->
                    verify(states)
                            .markFailed(
                                    tenantId,
                                    messageId,
                                    ((DeliveryResult.Rejected) result).code(),
                                    ((DeliveryResult.Rejected) result).message());
        }
    }

    @ParameterizedTest(name = "retryable provider failure attempt {0}")
    @MethodSource("retryableAttempts")
    void retryExhaustionBoundaryIsExact(int attempt, boolean retries) {
        claim(attempt);
        DeliveryResult.Rejected rejected =
                new DeliveryResult.Rejected(
                        DeliveryFailureKind.RETRYABLE, "TEMPORARY", "provider unavailable");
        when(gateway.deliver(any())).thenReturn(rejected);

        worker.deliver(tenantId, messageId);

        if (retries) {
            verify(states)
                    .scheduleRetry(
                            tenantId,
                            messageId,
                            new MessageRetryPolicy().nextRetryAt(attempt, NOW),
                            "TEMPORARY",
                            "provider unavailable");
            verify(states, never()).markFailed(any(), any(), any(), any());
        } else {
            verify(states).markFailed(tenantId, messageId, "TEMPORARY", "provider unavailable");
            verify(states, never()).scheduleRetry(any(), any(), any(), any(), any());
        }
    }

    @ParameterizedTest(name = "attachment failure {0} at processing attempt {1}")
    @MethodSource("attachmentFailures")
    void attachmentFailuresNeverInvokeProvider(
            DeliveryFailureKind kind, int attempt, boolean retries) {
        when(states.begin(tenantId, messageId))
                .thenReturn(Optional.of(snapshot(CommunicationChannel.EMAIL, attempt)));
        AttachmentResolutionException failure =
                new AttachmentResolutionException(
                        "ATTACHMENT_ERROR", kind, "attachment resolution failed");
        when(attachments.resolve(tenantId, messageId)).thenThrow(failure);

        worker.deliver(tenantId, messageId);

        verify(states, never()).beginProviderAttempt(any(), any());
        verify(gateway, never()).deliver(any());
        if (retries) {
            verify(states)
                    .scheduleRetry(
                            tenantId,
                            messageId,
                            new MessageRetryPolicy().nextRetryAt(attempt, NOW),
                            "ATTACHMENT_ERROR",
                            "attachment resolution failed");
        } else {
            verify(states)
                    .markFailed(
                            tenantId,
                            messageId,
                            "ATTACHMENT_ERROR",
                            "attachment resolution failed");
        }
    }

    private void claim(int attempt) {
        when(states.begin(tenantId, messageId))
                .thenReturn(Optional.of(snapshot(CommunicationChannel.EMAIL, attempt)));
        when(states.beginProviderAttempt(tenantId, messageId))
                .thenReturn(Optional.of(new ProviderAttemptSnapshot(DELIVERY_KEY, attempt)));
        when(attachments.resolve(tenantId, messageId)).thenReturn(List.of());
    }

    private MessageDeliverySnapshot snapshot(CommunicationChannel channel, int attempt) {
        return new MessageDeliverySnapshot(
                messageId,
                tenantId,
                UUID.randomUUID(),
                channel,
                destination(channel),
                channel == CommunicationChannel.EMAIL ? "Reminder" : null,
                "body",
                Math.max(0, attempt - 1),
                attempt);
    }

    private String destination(CommunicationChannel channel) {
        return switch (channel) {
            case EMAIL -> "customer@example.test";
            case SMS, WHATSAPP -> "+77010000000";
            case TELEGRAM -> "telegram-user-42";
            case IN_APP -> "device-token-42";
        };
    }

    private static Stream<Arguments> providerOutcomes() {
        return Stream.of(
                Arguments.of(
                        "accepted",
                        new DeliveryResult.Accepted("provider-id"),
                        ExpectedTransition.SENT),
                Arguments.of(
                        "ambiguous",
                        new DeliveryResult.Unknown("PROVIDER_TIMEOUT", "response lost"),
                        ExpectedTransition.UNKNOWN),
                Arguments.of(
                        "retryable",
                        new DeliveryResult.Rejected(
                                DeliveryFailureKind.RETRYABLE,
                                "PROVIDER_503",
                                "temporarily unavailable"),
                        ExpectedTransition.RETRY),
                Arguments.of(
                        "rate-limit",
                        new DeliveryResult.Rejected(
                                DeliveryFailureKind.RETRYABLE, "PROVIDER_429", "rate limited"),
                        ExpectedTransition.RETRY),
                Arguments.of(
                        "permanent",
                        new DeliveryResult.Rejected(
                                DeliveryFailureKind.PERMANENT,
                                "INVALID_DESTINATION",
                                "invalid recipient"),
                        ExpectedTransition.FAILED),
                Arguments.of(
                        "provider-auth",
                        new DeliveryResult.Rejected(
                                DeliveryFailureKind.PERMANENT,
                                "PROVIDER_401",
                                "provider rejected credentials"),
                        ExpectedTransition.FAILED));
    }

    private static Stream<Arguments> retryableAttempts() {
        return Stream.of(
                Arguments.of(1, true),
                Arguments.of(2, true),
                Arguments.of(3, true),
                Arguments.of(4, false),
                Arguments.of(5, false));
    }

    private static Stream<Arguments> attachmentFailures() {
        return Stream.of(
                Arguments.of(DeliveryFailureKind.RETRYABLE, 1, true),
                Arguments.of(DeliveryFailureKind.RETRYABLE, 3, true),
                Arguments.of(DeliveryFailureKind.RETRYABLE, 4, false),
                Arguments.of(DeliveryFailureKind.PERMANENT, 1, false),
                Arguments.of(DeliveryFailureKind.PERMANENT, 4, false));
    }

    private enum ExpectedTransition {
        SENT,
        UNKNOWN,
        RETRY,
        FAILED
    }
}
