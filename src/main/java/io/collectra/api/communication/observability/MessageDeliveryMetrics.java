package io.collectra.api.communication.observability;

import io.collectra.api.communication.application.DeliveryErrorSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MessageDeliveryMetrics {
    private final MeterRegistry registry;
    private final DeliveryErrorSummary errorSummary;

    public MessageDeliveryMetrics(MeterRegistry registry, DeliveryErrorSummary errorSummary) {
        this.registry = registry;
        this.errorSummary = errorSummary;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutcome(DeliveryOutcomeEvent event) {
        String channel = event.channel().name().toLowerCase();
        String result = result(event.outcome());
        Counter.builder("collectra_message_delivery_total")
                .tag("channel", channel)
                .tag("result", result)
                .register(registry)
                .increment();

        if (event.outcome() == DeliveryOutcomeEvent.Outcome.RETRY_SCHEDULED) {
            String code = errorSummary.normalizeCode(event.errorCode());
            Counter.builder("collectra_message_retry_total")
                    .tag("channel", channel)
                    .tag("error_code", code == null ? "UNKNOWN" : code)
                    .register(registry)
                    .increment();
        }

        if (event.outcome() == DeliveryOutcomeEvent.Outcome.SENT
                && event.messageCreatedAt() != null
                && event.outcomeAt() != null
                && !event.outcomeAt().isBefore(event.messageCreatedAt())) {
            Timer.builder("collectra_message_delivery_latency_seconds")
                    .tag("channel", channel)
                    .tag("result", result)
                    .register(registry)
                    .record(Duration.between(event.messageCreatedAt(), event.outcomeAt()));
        }
    }

    private static String result(DeliveryOutcomeEvent.Outcome outcome) {
        return switch (outcome) {
            case SENT -> "sent";
            case FAILED -> "failed";
            case RETRY_SCHEDULED -> "retry_scheduled";
            case UNKNOWN -> "unknown";
        };
    }
}
