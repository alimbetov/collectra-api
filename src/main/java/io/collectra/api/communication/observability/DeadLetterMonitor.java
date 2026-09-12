package io.collectra.api.communication.observability;

import io.collectra.api.communication.infrastructure.messaging.CommunicationMessagingConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "collectra.communication.delivery.enabled", havingValue = "true")
public class DeadLetterMonitor {
    private static final String REASON = "broker_dead_letter";

    private final AmqpAdmin amqpAdmin;
    private final AtomicLong depth = new AtomicLong();
    private final Counter arrivals;
    private long lastObservedDepth;

    public DeadLetterMonitor(AmqpAdmin amqpAdmin, MeterRegistry registry) {
        this.amqpAdmin = amqpAdmin;
        this.arrivals =
                Counter.builder("collectra_message_dead_letter_total")
                        .tag("queue", CommunicationMessagingConfig.DEAD_QUEUE)
                        .tag("reason", REASON)
                        .register(registry);
        Gauge.builder("collectra_message_dead_letter_depth", depth, AtomicLong::get)
                .tag("queue", CommunicationMessagingConfig.DEAD_QUEUE)
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${collectra.communication.dead-letter-refresh-delay:30s}")
    public synchronized void refresh() {
        Properties properties =
                amqpAdmin.getQueueProperties(CommunicationMessagingConfig.DEAD_QUEUE);
        if (properties == null) {
            depth.set(0L);
            lastObservedDepth = 0L;
            return;
        }
        Object countValue = properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        long current = countValue instanceof Number number ? number.longValue() : 0L;
        if (current > lastObservedDepth) {
            arrivals.increment(current - lastObservedDepth);
        }
        depth.set(current);
        lastObservedDepth = current;
    }
}
