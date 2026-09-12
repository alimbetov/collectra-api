package io.collectra.api.shared.outbox;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {
    private final OutboxRepository repository;
    private final Clock clock;

    public OutboxService(OutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void append(
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload) {
        repository.save(
                new OutboxEvent(
                        tenantId, aggregateType, aggregateId, eventType, payload, clock.instant()));
    }
}
