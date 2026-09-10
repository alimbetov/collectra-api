package io.collectra.api.shared.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OutboxStateService {
    private final OutboxRepository events;

    public OutboxStateService(OutboxRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Optional<PublishableEvent> loadForPublish(UUID eventId, String workerId) {
        return events.findById(eventId)
                .filter(event -> event.ownedBy(workerId))
                .map(PublishableEvent::from);
    }

    @Transactional
    public boolean markPublished(UUID eventId, String workerId, Instant now) {
        Optional<OutboxEvent> event = owned(eventId, workerId);
        event.ifPresent(value -> value.markPublished(now));
        return event.isPresent();
    }

    @Transactional
    public boolean scheduleRetry(
            UUID eventId,
            String workerId,
            Instant nextAttemptAt,
            String code,
            String message) {
        Optional<OutboxEvent> event = owned(eventId, workerId);
        event.ifPresent(value -> value.scheduleRetry(nextAttemptAt, code, message));
        return event.isPresent();
    }

    @Transactional
    public boolean markDead(UUID eventId, String workerId, String code, String message) {
        Optional<OutboxEvent> event = owned(eventId, workerId);
        event.ifPresent(value -> value.markDead(code, message));
        return event.isPresent();
    }

    private Optional<OutboxEvent> owned(UUID eventId, String workerId) {
        return events.findById(eventId).filter(event -> event.ownedBy(workerId));
    }

    public record PublishableEvent(
            UUID id,
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            int attemptCount) {
        static PublishableEvent from(OutboxEvent event) {
            return new PublishableEvent(
                    event.getId(),
                    event.getTenantId(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getEventType(),
                    event.getPayload(),
                    event.getAttemptCount());
        }
    }
}
