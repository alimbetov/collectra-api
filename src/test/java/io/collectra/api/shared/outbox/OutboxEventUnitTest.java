package io.collectra.api.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventUnitTest {
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    @Test
    void claimAndPublishFollowExplicitStateMachine() {
        OutboxEvent event = event();

        event.claim("worker-a", NOW);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.ownedBy("worker-a")).isTrue();

        event.markPublished(NOW.plusSeconds(1));
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(event.getLockedAt()).isNull();
        assertThat(event.getLockedBy()).isNull();

        assertThatThrownBy(() -> event.claim("worker-b", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryClearsOwnershipAndCanBeClaimedAgainWhenReady() {
        OutboxEvent event = event();
        event.claim("worker-a", NOW);
        event.scheduleRetry(NOW.plusSeconds(60), "BROKER_NACK", "nack");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY_WAIT);
        assertThat(event.getLastErrorCode()).isEqualTo("BROKER_NACK");
        assertThat(event.ownedBy("worker-a")).isFalse();

        assertThatThrownBy(() -> event.claim("worker-b", NOW.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class);

        event.claim("worker-b", NOW.plusSeconds(60));
        assertThat(event.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void markDeadClearsOwnership() {
        OutboxEvent event = event();
        event.claim("worker-a", NOW);

        event.markDead("BROKER_MAX_ATTEMPTS", "publisher failed");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(event.getLockedAt()).isNull();
        assertThat(event.getLockedBy()).isNull();
        assertThat(event.getLastErrorCode()).isEqualTo("BROKER_MAX_ATTEMPTS");
        assertThat(event.getLastErrorMessage()).isEqualTo("publisher failed");
        assertThat(event.ownedBy("worker-a")).isFalse();
    }

    @Test
    void publishedEventCannotBeRetried() {
        OutboxEvent event = event();
        event.claim("worker-a", NOW);
        event.markPublished(NOW.plusSeconds(1));

        assertThatThrownBy(
                        () -> event.scheduleRetry(NOW.plusSeconds(60), "BROKER_NACK", "late retry"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recoveryDoesNotIncrementAttemptCount() {
        OutboxEvent event = event();
        event.claim("worker-a", NOW);

        event.recover(NOW.plusSeconds(120), "PROCESSING_TIMEOUT_RECOVERED", "publisher stopped");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY_WAIT);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLockedAt()).isNull();
        assertThat(event.getLockedBy()).isNull();
    }

    private OutboxEvent event() {
        return new OutboxEvent(
                null,
                "GENERATION_JOB",
                UUID.randomUUID(),
                "DOCUMENT_GENERATION_REQUESTED",
                "{\"jobId\":\"" + UUID.randomUUID() + "\"}",
                NOW);
    }
}
