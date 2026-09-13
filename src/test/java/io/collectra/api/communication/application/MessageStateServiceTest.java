package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.domain.CampaignRunStatus;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.DeliveryAttemptStatus;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.domain.MessageDeliveryAttempt;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.communication.infrastructure.MessageDeliveryAttemptRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.communication.observability.DeliveryOutcomeEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MessageStateServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
    private static final UUID TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Mock MessageRepository messages;
    @Mock MessageAttachmentRepository attachments;
    @Mock MessageDeliveryAttemptRepository deliveryAttempts;
    @Mock CampaignRunRepository runs;
    @Mock ApplicationEventPublisher events;

    private MessageStateService service;

    @BeforeEach
    void setUp() {
        service =
                new MessageStateService(
                        messages,
                        attachments,
                        deliveryAttempts,
                        runs,
                        new MessageRetryPolicy(),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        events);
    }

    @Test
    void beginClaimsQueuedMessageWithoutCountingProviderCall() {
        CampaignRun run = runningRun(1);
        Message message = queued(run);
        stubMessage(message);
        when(attachments.existsRequiredNotReady(
                        TENANT, message.getId(), MessageAttachmentStatus.READY))
                .thenReturn(false);

        MessageDeliverySnapshot snapshot = service.begin(TENANT, message.getId()).orElseThrow();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.PROCESSING);
        assertThat(message.getAttemptCount()).isZero();
        assertThat(message.getProcessingAttemptCount()).isOne();
        assertThat(message.getProcessingStartedAt()).isEqualTo(NOW);
        assertThat(snapshot.messageId()).isEqualTo(message.getId());
        assertThat(snapshot.tenantId()).isEqualTo(TENANT);
        assertThat(snapshot.campaignRunId()).isEqualTo(run.getId());
        assertThat(snapshot.channel()).isEqualTo(CommunicationChannel.EMAIL);
        assertThat(snapshot.destination()).isEqualTo("customer@example.test");
        assertThat(snapshot.subject()).isEqualTo("Reminder");
        assertThat(snapshot.body()).isEqualTo("<p>Pay</p>");
        assertThat(snapshot.attemptCount()).isZero();
        assertThat(snapshot.processingAttemptCount()).isOne();
    }

    @Test
    void beginProviderAttemptPersistsStableDeliveryIdentityAndCountsPhysicalCall() {
        CampaignRun run = runningRun(1);
        Message message = processing(run, NOW.minusSeconds(1));
        stubMessage(message);
        when(deliveryAttempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderAttemptSnapshot snapshot =
                service.beginProviderAttempt(TENANT, message.getId()).orElseThrow();

        assertThat(message.getAttemptCount()).isOne();
        assertThat(snapshot.deliveryKey()).isEqualTo(message.getDeliveryKey());
        assertThat(snapshot.attemptNo()).isOne();
        ArgumentCaptor<MessageDeliveryAttempt> attempt =
                ArgumentCaptor.forClass(MessageDeliveryAttempt.class);
        verify(deliveryAttempts).save(attempt.capture());
        assertThat(attempt.getValue().getStatus()).isEqualTo(DeliveryAttemptStatus.STARTED);
        assertThat(attempt.getValue().getDeliveryKey()).isEqualTo(message.getDeliveryKey());
    }

    @Test
    void beginFailsClosedWhenRequiredAttachmentIsNotReady() {
        CampaignRun run = runningRun(1);
        Message message = queued(run);
        stubMessage(message);
        when(attachments.existsRequiredNotReady(
                        TENANT, message.getId(), MessageAttachmentStatus.READY))
                .thenReturn(true);

        assertThat(service.begin(TENANT, message.getId())).isEmpty();
        assertThat(message.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(message.getAttemptCount()).isZero();
        assertThat(message.getProcessingAttemptCount()).isZero();
    }

    @Test
    void beginIsIdempotentForAlreadyClaimedMessage() {
        CampaignRun run = runningRun(1);
        Message processing = queued(run);
        processing.beginAttempt(NOW.minusSeconds(1));
        stubMessage(processing);

        assertThat(service.begin(TENANT, processing.getId())).isEmpty();
        verify(attachments, never()).existsRequiredNotReady(any(), any(), any());
    }

    @Test
    void tenantMismatchDoesNotRevealMessage() {
        UUID messageId = UUID.randomUUID();
        when(messages.findLockedByIdAndTenantId(TENANT, messageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.begin(TENANT, messageId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Message not found");
    }

    @Test
    void markSentUpdatesMessageAndCounterExactlyOnceAndPublishesOutcome() {
        CampaignRun run = runningRun(1);
        Message message = processing(run, NOW.minusSeconds(5));
        stubMessageAndRun(message, run);

        assertThat(service.markSent(TENANT, message.getId(), "provider-42")).isTrue();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(message.getProviderMessageId()).isEqualTo("provider-42");
        assertThat(message.getSentAt()).isEqualTo(NOW);
        assertThat(run.getSentCount()).isOne();
        assertThat(run.getFailedCount()).isZero();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isEqualTo(NOW);
        verifyOutcome(DeliveryOutcomeEvent.Outcome.SENT, null);
    }

    @Test
    void unknownOutcomeDoesNotBlindlyRetryOrSettleRun() {
        CampaignRun run = runningRun(1);
        Message message = processing(run, NOW.minusSeconds(5));
        startProviderAttempt(message);
        MessageDeliveryAttempt attempt = startedAttempt(message);
        when(deliveryAttempts.findLockedByMessageIdAndAttemptNo(message.getId(), 1))
                .thenReturn(Optional.of(attempt));
        stubMessage(message);

        assertThat(
                        service.markUnknown(
                                TENANT, message.getId(), "PROVIDER_TIMEOUT", "response lost"))
                .isTrue();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.UNKNOWN);
        assertThat(message.getNextRetryAt()).isNull();
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.UNKNOWN);
        assertThat(run.getSentCount()).isZero();
        assertThat(run.getFailedCount()).isZero();
        assertThat(run.getRetryCount()).isZero();
        verify(runs, never()).findLockedByIdAndTenantId(any(), any());
        verifyOutcome(DeliveryOutcomeEvent.Outcome.UNKNOWN, "PROVIDER_TIMEOUT");
    }

    @Test
    void lateAcceptanceReconcilesUnknownExactlyOnce() {
        CampaignRun run = runningRun(1);
        Message message = processing(run, NOW.minusSeconds(5));
        startProviderAttempt(message);
        MessageDeliveryAttempt attempt = startedAttempt(message);
        attempt.unknown("PROVIDER_TIMEOUT", NOW.minusSeconds(1));
        message.markUnknown("PROVIDER_TIMEOUT", "response lost");
        stubMessageAndRun(message, run);
        when(deliveryAttempts.findLockedByMessageIdAndAttemptNo(message.getId(), 1))
                .thenReturn(Optional.of(attempt));

        assertThat(service.markSent(TENANT, message.getId(), "provider-42")).isTrue();
        assertThat(service.markSent(TENANT, message.getId(), "provider-42")).isFalse();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.ACCEPTED);
        assertThat(run.getSentCount()).isOne();
    }

    @Test
    void staleSentCallbackIsNoOpAndNeverTouchesRun() {
        CampaignRun run = runningRun(1);
        Message message = queued(run);
        stubMessage(message);

        assertThat(service.markSent(TENANT, message.getId(), "late")).isFalse();

        verify(runs, never()).findLockedByIdAndTenantId(any(), any());
        verify(events, never()).publishEvent(any());
        assertThat(run.getSentCount()).isZero();
    }

    @Test
    void retryTransitionIncrementsRetryCounterButNotTerminalCounters() {
        CampaignRun run = runningRun(1);
        Message message = processing(run, NOW.minusSeconds(5));
        stubMessageAndRun(message, run);
        Instant retryAt = NOW.plusSeconds(60);

        assertThat(
                        service.scheduleRetry(
                                TENANT,
                                message.getId(),
                                retryAt,
                                "TEMPORARY_PROVIDER_ERROR",
                                "retry later"))
                .isTrue();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.RETRY_WAIT);
        assertThat(message.getNextRetryAt()).isEqualTo(retryAt);
        assertThat(message.getLastErrorCode()).isEqualTo("TEMPORARY_PROVIDER_ERROR");
        assertThat(run.getRetryCount()).isOne();
        assertThat(run.getSentCount()).isZero();
        assertThat(run.getFailedCount()).isZero();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.RUNNING);
        verifyOutcome(DeliveryOutcomeEvent.Outcome.RETRY_SCHEDULED, "TEMPORARY_PROVIDER_ERROR");
    }

    @Test
    void staleRetryCallbackIsNoOpAndDoesNotDoubleCount() {
        CampaignRun run = runningRun(1);
        Message message = queued(run);
        stubMessage(message);

        assertThat(
                        service.scheduleRetry(
                                TENANT, message.getId(), NOW.plusSeconds(60), "TEMP", "late"))
                .isFalse();

        assertThat(run.getRetryCount()).isZero();
        verify(runs, never()).findLockedByIdAndTenantId(any(), any());
    }

    @Test
    void failureTransitionCompletesRunExactlyOnce() {
        CampaignRun run = runningRun(1);
        Message message = processing(run, NOW.minusSeconds(5));
        stubMessageAndRun(message, run);

        assertThat(service.markFailed(TENANT, message.getId(), "PERMANENT", "rejected")).isTrue();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(run.getFailedCount()).isOne();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.COMPLETED);
        verifyOutcome(DeliveryOutcomeEvent.Outcome.FAILED, "PERMANENT");
    }

    @Test
    void preDeliveryFailureIsAllowedOnlyFromQueued() {
        CampaignRun run = runningRun(1);
        Message message = queued(run);
        stubMessageAndRun(message, run);

        assertThat(
                        service.failBeforeDelivery(
                                TENANT, message.getId(), "MATERIALIZATION_FAILED", "bad template"))
                .isTrue();
        assertThat(message.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(message.getAttemptCount()).isZero();
        assertThat(run.getFailedCount()).isOne();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.COMPLETED);

        assertThat(
                        service.failBeforeDelivery(
                                TENANT, message.getId(), "MATERIALIZATION_FAILED", "duplicate"))
                .isFalse();
        assertThat(run.getFailedCount()).isOne();
    }

    @Test
    void recoveryIgnoresExactCutoffAndNonProcessingStates() {
        Instant cutoff = NOW.minusSeconds(300);
        CampaignRun run = runningRun(2);

        Message exactBoundary = processing(run, cutoff);
        stubMessage(exactBoundary);
        assertThat(service.recoverStale(TENANT, exactBoundary.getId(), cutoff, NOW)).isFalse();

        Message queued = queued(run);
        stubMessage(queued);
        assertThat(service.recoverStale(TENANT, queued.getId(), cutoff, NOW)).isFalse();

        verify(runs, never()).findLockedByIdAndTenantId(any(), any());
    }

    @Test
    void recoverySchedulesRetryWhenProviderWasNeverCalled() {
        Instant cutoff = NOW.minusSeconds(300);
        CampaignRun run = runningRun(1);
        Message message = processing(run, cutoff.minusSeconds(1));
        stubMessageAndRun(message, run);

        assertThat(service.recoverStale(TENANT, message.getId(), cutoff, NOW)).isTrue();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.RETRY_WAIT);
        assertThat(message.getNextRetryAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(message.getLastErrorCode()).isEqualTo(MessageRecoveryService.PROCESSING_TIMEOUT);
        assertThat(run.getRetryCount()).isOne();
        assertThat(run.getFailedCount()).isZero();
        verifyOutcome(
                DeliveryOutcomeEvent.Outcome.RETRY_SCHEDULED,
                MessageRecoveryService.PROCESSING_TIMEOUT);
    }

    @Test
    void recoveryTurnsInFlightProviderAttemptIntoUnknownInsteadOfRetrying() {
        Instant cutoff = NOW.minusSeconds(300);
        CampaignRun run = runningRun(1);
        Message message = processing(run, cutoff.minusSeconds(1));
        startProviderAttempt(message);
        MessageDeliveryAttempt attempt = startedAttempt(message);
        when(deliveryAttempts.findLockedByMessageIdAndAttemptNo(message.getId(), 1))
                .thenReturn(Optional.of(attempt));
        stubMessage(message);

        assertThat(service.recoverStale(TENANT, message.getId(), cutoff, NOW)).isTrue();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.UNKNOWN);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.UNKNOWN);
        assertThat(run.getRetryCount()).isZero();
        verify(runs, never()).findLockedByIdAndTenantId(any(), any());
    }

    @Test
    void recoveryFailsExhaustedProcessingAttemptAndCompletesRun() {
        Instant cutoff = NOW.minusSeconds(300);
        CampaignRun run = runningRun(1);
        Message message = processingAtAttempt(run, 4, cutoff.minusSeconds(1));
        stubMessageAndRun(message, run);

        assertThat(service.recoverStale(TENANT, message.getId(), cutoff, NOW)).isTrue();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(message.getLastErrorCode()).isEqualTo(MessageRecoveryService.PROCESSING_TIMEOUT);
        assertThat(run.getRetryCount()).isZero();
        assertThat(run.getFailedCount()).isOne();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.COMPLETED);
        verifyOutcome(
                DeliveryOutcomeEvent.Outcome.FAILED, MessageRecoveryService.PROCESSING_TIMEOUT);
    }

    @Test
    void missingCampaignRunFailsBeforeAnyCounterMutationOrOutcomeEvent() {
        CampaignRun run = runningRun(1);
        Message message = processing(run, NOW.minusSeconds(1));
        stubMessage(message);
        when(runs.findLockedByIdAndTenantId(run.getId(), TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markSent(TENANT, message.getId(), "provider"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Campaign run not found");

        assertThat(run.getSentCount()).isZero();
        verify(events, never()).publishEvent(any());
    }

    private void verifyOutcome(DeliveryOutcomeEvent.Outcome expected, String code) {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        DeliveryOutcomeEvent outcome = (DeliveryOutcomeEvent) event.getValue();
        assertThat(outcome.outcome()).isEqualTo(expected);
        assertThat(outcome.errorCode()).isEqualTo(code);
        assertThat(outcome.outcomeAt()).isEqualTo(NOW);
    }

    private CampaignRun runningRun(int recipients) {
        CampaignRun run = new CampaignRun(TENANT, UUID.randomUUID());
        run.ready(recipients, NOW.minusSeconds(20));
        run.start(NOW.minusSeconds(10));
        return run;
    }

    private Message queued(CampaignRun run) {
        return Message.queued(
                TENANT,
                run.getCampaignId(),
                run.getId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommunicationChannel.EMAIL,
                "customer@example.test",
                "ru-KZ",
                "Reminder",
                "<p>Pay</p>");
    }

    private Message processing(CampaignRun run, Instant startedAt) {
        Message message = queued(run);
        message.beginAttempt(startedAt);
        return message;
    }

    private void startProviderAttempt(Message message) {
        message.beginProviderAttempt();
    }

    private MessageDeliveryAttempt startedAttempt(Message message) {
        return MessageDeliveryAttempt.started(
                TENANT, message.getId(), message.getAttemptCount(), message.getDeliveryKey(), NOW.minusSeconds(4));
    }

    private Message processingAtAttempt(
            CampaignRun run, int targetAttempt, Instant finalStartedAt) {
        Message message = queued(run);
        Instant cursor = finalStartedAt.minusSeconds(targetAttempt * 10L);
        for (int attempt = 1; attempt <= targetAttempt; attempt++) {
            Instant started =
                    attempt == targetAttempt ? finalStartedAt : cursor.plusSeconds(attempt * 10L);
            message.beginAttempt(started);
            if (attempt < targetAttempt) {
                message.scheduleRetry(started.plusSeconds(1), "TEMP", "temporary");
                message.requeue();
            }
        }
        return message;
    }

    private void stubMessage(Message message) {
        when(messages.findLockedByIdAndTenantId(TENANT, message.getId()))
                .thenReturn(Optional.of(message));
    }

    private void stubMessageAndRun(Message message, CampaignRun run) {
        stubMessage(message);
        when(runs.findLockedByIdAndTenantId(run.getId(), TENANT)).thenReturn(Optional.of(run));
    }
}
