package io.collectra.api.communication.application;

import io.collectra.api.communication.infrastructure.MessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MessageRecoveryService {
    static final String PROCESSING_TIMEOUT = "PROCESSING_TIMEOUT";
    static final String PROCESSING_TIMEOUT_MESSAGE =
            "Delivery attempt did not finish before the processing timeout";

    private final MessageRepository messages;
    private final MessageStateService states;
    private final Clock clock;
    private final Duration processingTimeout;
    private final int batchSize;

    public MessageRecoveryService(
            MessageRepository messages,
            MessageStateService states,
            Clock clock,
            @Value("${collectra.communication.processing-timeout:5m}") Duration processingTimeout,
            @Value("${collectra.communication.recovery-batch-size:100}") int batchSize) {
        if (processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("processingTimeout must be positive");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("recoveryBatchSize must be positive");
        }
        this.messages = messages;
        this.states = states;
        this.clock = clock;
        this.processingTimeout = processingTimeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${collectra.communication.recovery-delay:1m}")
    public int recoverStale() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(processingTimeout);
        var candidates = messages.findStaleProcessingCandidates(cutoff, batchSize);
        int recovered = 0;
        for (var candidate : candidates) {
            if (states.recoverStale(candidate.getTenantId(), candidate.getId(), cutoff, now)) {
                recovered++;
            }
        }
        return recovered;
    }
}
