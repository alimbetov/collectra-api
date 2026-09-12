package io.collectra.api.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CampaignRunScenarioMatrixTest {
    private static final Instant PREPARED = Instant.parse("2026-09-12T10:00:00Z");
    private static final Instant STARTED = PREPARED.plusSeconds(1);
    private static final Instant COMPLETED = STARTED.plusSeconds(60);

    @ParameterizedTest(name = "terminal mix sent={0}, failed={1}, skipped={2}")
    @MethodSource("terminalMixes")
    void everyTerminalMixCompletesExactlyAtRecipientCount(int sent, int failed, int skipped) {
        int recipients = sent + failed + skipped;
        CampaignRun run = running(recipients);

        for (int i = 0; i < sent; i++) run.messageSent();
        for (int i = 0; i < failed; i++) run.messageFailed();
        if (skipped > 0) run.recipientsSkipped(skipped);

        assertThat(run.terminalCount()).isEqualTo(recipients);
        assertThat(run.completeIfTerminal(COMPLETED)).isTrue();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.COMPLETED);
        assertThat(run.getSentCount()).isEqualTo(sent);
        assertThat(run.getFailedCount()).isEqualTo(failed);
        assertThat(run.getSkippedCount()).isEqualTo(skipped);
        assertThat(run.getCompletedAt()).isEqualTo(COMPLETED);
    }

    @ParameterizedTest(name = "retry count {0} never settles recipients")
    @MethodSource("retryCounts")
    void retriesNeverChangeTerminalProgress(int retries) {
        CampaignRun run = running(1);
        for (int i = 0; i < retries; i++) run.messageRetryScheduled();

        assertThat(run.getRetryCount()).isEqualTo(retries);
        assertThat(run.terminalCount()).isZero();
        assertThat(run.completeIfTerminal(COMPLETED)).isFalse();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.RUNNING);
    }

    @Test
    void zeroRecipientRunCanCompleteImmediatelyAfterStart() {
        CampaignRun run = running(0);
        assertThat(run.completeIfTerminal(COMPLETED)).isTrue();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.COMPLETED);
    }

    @ParameterizedTest(name = "negative count {0}")
    @MethodSource("negativeCounts")
    void negativePreparationAndSkipCountsAreRejected(String operation, int count) {
        if ("ready".equals(operation)) {
            CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
            assertThatThrownBy(() -> run.ready(count, PREPARED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("recipientCount");
            assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.PREPARING);
        } else {
            CampaignRun run = running(2);
            assertThatThrownBy(() -> run.recipientsSkipped(count))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
            assertThat(run.getSkippedCount()).isZero();
        }
    }

    @ParameterizedTest(name = "overflow after {0}")
    @MethodSource("overflowOperations")
    void terminalCountersCannotExceedRecipientCount(String operation) {
        CampaignRun run = running(1);
        run.messageSent();

        assertThatThrownBy(
                        () -> {
                            switch (operation) {
                                case "sent" -> run.messageSent();
                                case "failed" -> run.messageFailed();
                                case "skipped-one" -> run.recipientSkipped();
                                case "skipped-many" -> run.recipientsSkipped(2);
                                default -> throw new IllegalArgumentException(operation);
                            }
                        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceed recipientCount");

        assertThat(run.getSentCount()).isOne();
        assertThat(run.terminalCount()).isOne();
    }

    @ParameterizedTest(name = "incomplete terminals={0}/{1}")
    @MethodSource("incompleteProgress")
    void completeIfTerminalIsNoOpBeforeAllRecipientsSettle(int recipients, int sent) {
        CampaignRun run = running(recipients);
        for (int i = 0; i < sent; i++) run.messageSent();

        assertThat(run.completeIfTerminal(COMPLETED)).isFalse();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.RUNNING);
        assertThat(run.getCompletedAt()).isNull();
    }

    @Test
    void explicitCompleteRejectsUnsettledRecipients() {
        CampaignRun run = running(2);
        run.messageSent();

        assertThatThrownBy(() -> run.complete(COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-terminal");
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.RUNNING);
    }

    @ParameterizedTest(name = "cancel from {0}")
    @MethodSource("cancellableRuns")
    void cancelIsAllowedOnlyForNonTerminalLifecycleStates(String state, CampaignRun run) {
        run.cancel(COMPLETED);
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.CANCELLED);
        assertThat(run.getCompletedAt()).isEqualTo(COMPLETED);
    }

    @ParameterizedTest(name = "finished state {0} cannot cancel")
    @MethodSource("finishedRuns")
    void finishedRunCannotBeCancelled(String state, CampaignRun run) {
        assertThatThrownBy(() -> run.cancel(COMPLETED.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @ParameterizedTest(name = "fail from {0}")
    @MethodSource("failableRuns")
    void failureIsAllowedFromPreparingAndRunning(String state, CampaignRun run) {
        run.fail(COMPLETED);
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.FAILED);
        assertThat(run.getCompletedAt()).isEqualTo(COMPLETED);
    }

    @Test
    void readyAndStartTimestampsAreDeterministic() {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        run.ready(3, PREPARED);
        run.start(STARTED);

        assertThat(run.getPreparedAt()).isEqualTo(PREPARED);
        assertThat(run.getStartedAt()).isEqualTo(STARTED);
        assertThat(run.getRecipientCount()).isEqualTo(3);
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.RUNNING);
    }

    @Test
    void lifecycleRejectsDuplicateReadyAndDuplicateStart() {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        run.ready(1, PREPARED);
        assertThatThrownBy(() -> run.ready(1, PREPARED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected PREPARING");
        run.start(STARTED);
        assertThatThrownBy(() -> run.start(STARTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected READY");
    }

    private static CampaignRun running(int recipients) {
        CampaignRun run = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        run.ready(recipients, PREPARED);
        run.start(STARTED);
        return run;
    }

    private static Stream<Arguments> terminalMixes() {
        return Stream.of(
                Arguments.of(1, 0, 0),
                Arguments.of(0, 1, 0),
                Arguments.of(0, 0, 1),
                Arguments.of(1, 1, 0),
                Arguments.of(1, 0, 1),
                Arguments.of(0, 1, 1),
                Arguments.of(2, 1, 1),
                Arguments.of(5, 0, 0),
                Arguments.of(0, 5, 0),
                Arguments.of(0, 0, 5));
    }

    private static Stream<Arguments> retryCounts() {
        return Stream.of(Arguments.of(0), Arguments.of(1), Arguments.of(2), Arguments.of(3), Arguments.of(10));
    }

    private static Stream<Arguments> negativeCounts() {
        return Stream.of(
                Arguments.of("ready", -1),
                Arguments.of("ready", -100),
                Arguments.of("skip", -1),
                Arguments.of("skip", -100));
    }

    private static Stream<Arguments> overflowOperations() {
        return Stream.of(
                Arguments.of("sent"),
                Arguments.of("failed"),
                Arguments.of("skipped-one"),
                Arguments.of("skipped-many"));
    }

    private static Stream<Arguments> incompleteProgress() {
        return Stream.of(
                Arguments.of(1, 0),
                Arguments.of(2, 0),
                Arguments.of(2, 1),
                Arguments.of(10, 9));
    }

    private static Stream<Arguments> cancellableRuns() {
        CampaignRun preparing = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        CampaignRun ready = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        ready.ready(1, PREPARED);
        CampaignRun running = running(1);
        return Stream.of(
                Arguments.of("PREPARING", preparing),
                Arguments.of("READY", ready),
                Arguments.of("RUNNING", running));
    }

    private static Stream<Arguments> finishedRuns() {
        CampaignRun completed = running(1);
        completed.messageSent();
        completed.complete(COMPLETED);
        CampaignRun failed = running(1);
        failed.fail(COMPLETED);
        CampaignRun cancelled = running(1);
        cancelled.cancel(COMPLETED);
        return Stream.of(
                Arguments.of("COMPLETED", completed),
                Arguments.of("FAILED", failed),
                Arguments.of("CANCELLED", cancelled));
    }

    private static Stream<Arguments> failableRuns() {
        CampaignRun preparing = new CampaignRun(UUID.randomUUID(), UUID.randomUUID());
        CampaignRun running = running(1);
        return Stream.of(Arguments.of("PREPARING", preparing), Arguments.of("RUNNING", running));
    }
}
