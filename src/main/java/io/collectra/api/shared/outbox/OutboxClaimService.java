package io.collectra.api.shared.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaimService {
    private static final int RECOVERY_BATCH_SIZE = 200;

    private final OutboxRepository events;
    private final OutboxMetrics metrics;

    public OutboxClaimService(OutboxRepository events, OutboxMetrics metrics) {
        this.events = events;
        this.metrics = metrics;
    }

    @Transactional
    public List<UUID> claimBatch(String workerId, Instant now, int batchSize) {
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
        List<OutboxEvent> claimed = events.findReadyForUpdate(now, batchSize);
        claimed.forEach(event -> event.claim(workerId, now));
        return claimed.stream().map(OutboxEvent::getId).toList();
    }

    @Transactional
    public int recoverStale(Instant now, Duration processingTimeout) {
        if (processingTimeout == null
                || processingTimeout.isNegative()
                || processingTimeout.isZero()) {
            throw new IllegalArgumentException("processingTimeout must be positive");
        }
        Instant cutoff = now.minus(processingTimeout);
        List<OutboxEvent> stale = events.findStaleForUpdate(cutoff, RECOVERY_BATCH_SIZE);
        stale.forEach(
                event ->
                        event.recover(
                                now,
                                "PROCESSING_TIMEOUT_RECOVERED",
                                "Recovered stale outbox event after publisher interruption"));
        metrics.recovered(stale.size());
        return stale.size();
    }
}
