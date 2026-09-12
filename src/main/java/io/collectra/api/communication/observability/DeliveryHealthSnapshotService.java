package io.collectra.api.communication.observability;

import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryHealthSnapshotService {
    private final MessageRepository messages;
    private final Clock clock;
    private final Duration processingTimeout;

    private final AtomicLong stuckCount = new AtomicLong();
    private final AtomicLong oldestStuckAgeSeconds = new AtomicLong();
    private final AtomicLong dueRetryCount = new AtomicLong();
    private final AtomicLong oldestDueRetryAgeSeconds = new AtomicLong();
    private final AtomicLong queueAgeSeconds = new AtomicLong();

    public DeliveryHealthSnapshotService(
            MessageRepository messages,
            Clock clock,
            MeterRegistry registry,
            @Value("${collectra.communication.processing-timeout:5m}") Duration processingTimeout) {
        this.messages = messages;
        this.clock = clock;
        this.processingTimeout = processingTimeout;

        Gauge.builder("collectra_message_processing_stuck", stuckCount, AtomicLong::get)
                .register(registry);
        Gauge.builder(
                        "collectra_message_processing_oldest_stuck_age_seconds",
                        oldestStuckAgeSeconds,
                        AtomicLong::get)
                .register(registry);
        Gauge.builder("collectra_message_retry_wait_due", dueRetryCount, AtomicLong::get)
                .register(registry);
        Gauge.builder(
                        "collectra_message_retry_wait_oldest_age_seconds",
                        oldestDueRetryAgeSeconds,
                        AtomicLong::get)
                .register(registry);
        Gauge.builder("collectra_message_queue_age_seconds", queueAgeSeconds, AtomicLong::get)
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${collectra.communication.health-refresh-delay:30s}")
    public void refresh() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(processingTimeout);

        stuckCount.set(
                messages.countByStatusAndProcessingStartedAtBefore(MessageStatus.PROCESSING, cutoff));
        oldestStuckAgeSeconds.set(
                messages.findFirstByStatusAndProcessingStartedAtBeforeOrderByProcessingStartedAtAsc(
                                MessageStatus.PROCESSING, cutoff)
                        .map(value -> age(now, value.getProcessingStartedAt()))
                        .orElse(0L));

        dueRetryCount.set(
                messages.countByStatusAndNextRetryAtLessThanEqual(MessageStatus.RETRY_WAIT, now));
        oldestDueRetryAgeSeconds.set(
                messages.findFirstByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                                MessageStatus.RETRY_WAIT, now)
                        .map(value -> age(now, value.getNextRetryAt()))
                        .orElse(0L));

        queueAgeSeconds.set(
                messages.findFirstByStatusOrderByCreatedAtAsc(MessageStatus.QUEUED)
                        .map(value -> age(now, value.getCreatedAt()))
                        .orElse(0L));
    }

    private static long age(Instant now, Instant value) {
        if (value == null || value.isAfter(now)) {
            return 0L;
        }
        return Duration.between(value, now).toSeconds();
    }
}
