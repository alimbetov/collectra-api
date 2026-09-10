package io.collectra.api.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OutboxMetricsUnitTest {
    @Test
    void recordsCountersAndDeadGaugeWithoutHighCardinalityTags() {
        OutboxRepository events = mock(OutboxRepository.class);
        when(events.countByStatus(OutboxEventStatus.DEAD)).thenReturn(3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxMetrics metrics = new OutboxMetrics(events, registry);

        metrics.published();
        metrics.retry();
        metrics.dead();
        metrics.recovered(2);

        assertThat(registry.get("collectra.outbox.published").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("collectra.outbox.retry").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("collectra.outbox.dead").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("collectra.outbox.recovered").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("collectra.outbox.dead.current").gauge().value()).isEqualTo(3.0);
    }
}
