package io.collectra.api.communication.application;

import io.collectra.api.communication.infrastructure.MessageRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class MessageRetryDispatcher {
    private final MessageRepository messages;
    private final Clock clock;
    private final int batchSize;

    public MessageRetryDispatcher(
            MessageRepository messages,
            Clock clock,
            @Value("${collectra.communication.retry-dispatch-batch-size:100}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("retryDispatchBatchSize must be positive");
        }
        this.messages = messages;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Transactional
    public int dispatchDue() {
        var due = messages.findDueRetryForUpdate(clock.instant(), batchSize);
        due.forEach(message -> message.requeue());
        return due.size();
    }
}
