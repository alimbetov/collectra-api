package io.collectra.api.communication.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.communication.application.DeliveryErrorSummary;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageDeliveryMetricsTest {
    @Test
    void retryUsesNormalizedLowCardinalityErrorCode() {
        var registry = new SimpleMeterRegistry();
        var metrics = new MessageDeliveryMetrics(registry, new DeliveryErrorSummary());

        metrics.onOutcome(
                event(
                        DeliveryOutcomeEvent.Outcome.RETRY_SCHEDULED,
                        "provider_specific_timeout_abc123",
                        Instant.parse("2026-09-12T10:00:00Z"),
                        Instant.parse("2026-09-12T10:00:10Z")));

        assertThat(
                        registry.get("collectra_message_delivery_total")
                                .tags("channel", "email", "result", "retry_scheduled")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(
                        registry.get("collectra_message_retry_total")
                                .tags("channel", "email", "error_code", "CONNECTION_TIMEOUT")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    void sentRecordsCreatedToSentLatency() {
        var registry = new SimpleMeterRegistry();
        var metrics = new MessageDeliveryMetrics(registry, new DeliveryErrorSummary());

        metrics.onOutcome(
                event(
                        DeliveryOutcomeEvent.Outcome.SENT,
                        null,
                        Instant.parse("2026-09-12T10:00:00Z"),
                        Instant.parse("2026-09-12T10:00:05Z")));

        assertThat(
                        registry.get("collectra_message_delivery_latency_seconds")
                                .tags("channel", "email", "result", "sent")
                                .timer()
                                .totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isEqualTo(5.0);
    }

    private DeliveryOutcomeEvent event(
            DeliveryOutcomeEvent.Outcome outcome,
            String errorCode,
            Instant createdAt,
            Instant outcomeAt) {
        return new DeliveryOutcomeEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommunicationChannel.EMAIL,
                1,
                outcome,
                errorCode,
                createdAt,
                outcomeAt);
    }
}
