package io.collectra.api.communication.application;

import io.collectra.api.communication.infrastructure.MessageRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class MessageRecoveryService {
    static final String PROCESSING_TIMEOUT = "PROCESSING_TIMEOUT";
    static final String PROCESSING_TIMEOUT_MESSAGE =
            "Delivery attempt did not finish before the processing timeout";

    private final MessageRepository messages;
    private final MessageRetryPolicy retryPolicy;
    private final Clock clock;
    private final Duration processingTimeout;
    private final int batchSize;

    public MessageRecoveryService(
            MessageRepository messages,
            MessageRetryPolicy retryPolicy,
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
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.processingTimeout = processingTimeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${collectra.communication.recovery-delay:1m}")
    @Transactional
    public int recoverStale() {
        Instant now = clock.instant();
        var stale = messages.findStaleProcessingForUpdate(now.minus(processingTimeout), batchSize);
        stale.forEach(
                message -> {
                    if (retryPolicy.exhausted(message.getAttemptCount())) {
                        message.markFailed(PROCESSING_TIMEOUT, PROCESSING_TIMEOUT_MESSAGE);
                    } else {
                        message.scheduleRetry(
                                retryPolicy.nextRetryAt(message.getAttemptCount(), now),
                                PROCESSING_TIMEOUT,
                                PROCESSING_TIMEOUT_MESSAGE);
                    }
                });
        return stale.size();
    }
}
