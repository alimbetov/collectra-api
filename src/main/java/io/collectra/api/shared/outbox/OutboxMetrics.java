package io.collectra.api.shared.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {
    private final Counter published;
    private final Counter retry;
    private final Counter dead;
    private final Counter recovered;

    public OutboxMetrics(OutboxRepository events, MeterRegistry registry) {
        this.published = registry.counter("collectra.outbox.published");
        this.retry = registry.counter("collectra.outbox.retry");
        this.dead = registry.counter("collectra.outbox.dead");
        this.recovered = registry.counter("collectra.outbox.recovered");

        Gauge.builder(
                        "collectra.outbox.dead.current",
                        events,
                        repository -> repository.countByStatus(OutboxEventStatus.DEAD))
                .description("Current number of DEAD outbox events")
                .register(registry);
    }

    public void published() {
        published.increment();
    }

    public void retry() {
        retry.increment();
    }

    public void dead() {
        dead.increment();
    }

    public void recovered(int count) {
        if (count > 0) recovered.increment(count);
    }
}
