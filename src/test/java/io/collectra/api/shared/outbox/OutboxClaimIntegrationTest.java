package io.collectra.api.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        properties = {
            "collectra.messaging.outbox-enabled=false",
            "spring.task.scheduling.enabled=false"
        })
class OutboxClaimIntegrationTest extends AbstractIntegrationTest {
    @Autowired private OutboxRepository events;
    @Autowired private OutboxClaimService claims;
    @Autowired private OutboxStateService states;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanBefore() {
        events.deleteAll();
    }

    @AfterEach
    void cleanAfter() {
        events.deleteAll();
    }

    @Test
    void skipLockedGivesConcurrentTransactionsDisjointClaims() throws Exception {
        Instant now = Instant.parse("2026-09-10T10:00:00Z");
        for (int i = 0; i < 4; i++) {
            events.save(
                    new OutboxEvent(
                            null,
                            "TEST",
                            UUID.randomUUID(),
                            "DOCUMENT_GENERATION_REQUESTED",
                            "{}",
                            now));
        }

        CyclicBarrier selected = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> selectAndHold("worker-a", now, selected));
            var second = pool.submit(() -> selectAndHold("worker-b", now, selected));

            List<UUID> a = first.get(10, TimeUnit.SECONDS);
            List<UUID> b = second.get(10, TimeUnit.SECONDS);

            assertThat(a).hasSize(2);
            assertThat(b).hasSize(2);
            HashSet<UUID> intersection = new HashSet<>(a);
            intersection.retainAll(b);
            assertThat(intersection).isEmpty();

            List<UUID> claimedIds = Stream.concat(a.stream(), b.stream()).toList();
            assertThat(events.findAllById(claimedIds))
                    .hasSize(4)
                    .allMatch(event -> event.getStatus() == OutboxEventStatus.PROCESSING);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void staleProcessingIsDurablyRecoveredAndCanBeReclaimedByAnotherWorker() {
        Instant claimedAt = Instant.parse("2026-09-10T10:00:00Z");
        OutboxEvent event =
                events.saveAndFlush(
                        new OutboxEvent(
                                null,
                                "TEST",
                                UUID.randomUUID(),
                                "DOCUMENT_GENERATION_REQUESTED",
                                "{}",
                                claimedAt));

        assertThat(claims.claimBatch("worker-crashed", claimedAt, 10))
                .containsExactly(event.getId());
        assertThat(states.loadForPublish(event.getId(), "worker-crashed")).isPresent();

        Instant recoveryAt = claimedAt.plus(Duration.ofMinutes(3));
        assertThat(claims.recoverStale(recoveryAt, Duration.ofMinutes(2))).isOne();

        OutboxEvent recovered = events.findById(event.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(OutboxEventStatus.RETRY_WAIT);
        assertThat(recovered.getAttemptCount()).isOne();
        assertThat(recovered.getLockedAt()).isNull();
        assertThat(recovered.getLockedBy()).isNull();
        assertThat(recovered.getLastErrorCode()).isEqualTo("PROCESSING_TIMEOUT_RECOVERED");
        assertThat(states.loadForPublish(event.getId(), "worker-crashed")).isEmpty();

        assertThat(claims.claimBatch("worker-replacement", recoveryAt, 10))
                .containsExactly(event.getId());
        OutboxEvent reclaimed = events.findById(event.getId()).orElseThrow();
        assertThat(reclaimed.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(reclaimed.getAttemptCount()).isEqualTo(2);
        assertThat(reclaimed.getLockedBy()).isEqualTo("worker-replacement");
        assertThat(states.loadForPublish(event.getId(), "worker-replacement")).isPresent();
    }

    private List<UUID> selectAndHold(String worker, Instant now, CyclicBarrier selected) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(
                status -> {
                    List<OutboxEvent> batch = events.findReadyForUpdate(now, 2);
                    try {
                        selected.await(5, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                    batch.forEach(event -> event.claim(worker, now));
                    return batch.stream().map(OutboxEvent::getId).toList();
                });
    }
}
