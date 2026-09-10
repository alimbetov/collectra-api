package io.collectra.api.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

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
    void recoveryDoesNotIncrementAttemptCount() {
        OutboxEvent event = event();
        event.claim("worker-a", NOW);

        event.recover(
                NOW.plusSeconds(120),
                "PROCESSING_TIMEOUT_RECOVERED",
                "publisher stopped");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY_WAIT);
        assertThat(event.getAttemptCount()).isEqualTo(1);
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
