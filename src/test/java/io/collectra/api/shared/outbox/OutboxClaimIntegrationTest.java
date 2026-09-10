package io.collectra.api.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.AbstractIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

class OutboxClaimIntegrationTest extends AbstractIntegrationTest {
    @Autowired private OutboxRepository events;
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
