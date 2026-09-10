package io.collectra.api.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "collectra.messaging.outbox-enabled", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxClaimService claims;
    private final OutboxStateService states;
    private final OutboxEventRouter router;
    private final OutboxRetryPolicy retryPolicy;
    private final OutboxMetrics metrics;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final Clock clock;
    private final long confirmTimeoutMs;
    private final int batchSize;
    private final Duration processingTimeout;
    private final String workerId = "outbox-" + UUID.randomUUID();

    public OutboxPublisher(
            OutboxClaimService claims,
            OutboxStateService states,
            OutboxEventRouter router,
            OutboxRetryPolicy retryPolicy,
            OutboxMetrics metrics,
            RabbitTemplate rabbit,
            ObjectMapper json,
            Clock clock,
            @Value("${collectra.messaging.publisher-confirm-timeout-ms:5000}") long confirmTimeoutMs,
            @Value("${collectra.messaging.outbox-batch-size:50}") int batchSize,
            @Value("${collectra.messaging.outbox-processing-timeout:PT2M}") Duration processingTimeout) {
        this.claims = claims;
        this.states = states;
        this.router = router;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.rabbit = rabbit;
        this.json = json;
        this.clock = clock;
        this.confirmTimeoutMs = confirmTimeoutMs;
        this.batchSize = batchSize;
        this.processingTimeout = processingTimeout;
    }

    @Scheduled(fixedDelayString = "${collectra.messaging.outbox-publish-delay-ms:1000}")
    public void publishPending() {
        Instant now = Instant.now(clock);
        for (UUID eventId : claims.claimBatch(workerId, now, batchSize)) {
            publishOne(eventId);
        }
    }

    @Scheduled(fixedDelayString = "${collectra.messaging.outbox-recovery-delay-ms:60000}")
    public void recoverStale() {
        claims.recoverStale(Instant.now(clock), processingTimeout);
    }

    void publishOne(UUID eventId) {
        Optional<OutboxStateService.PublishableEvent> loaded = states.loadForPublish(eventId, workerId);
        if (loaded.isEmpty()) return;
        OutboxStateService.PublishableEvent event = loaded.get();

        OutboxRoute route;
        JsonNode payload;
        try {
            route = router.route(event.eventType());
            payload = json.readTree(event.payload());
            if (payload == null) throw new JsonProcessingException("Empty JSON payload") {};
        } catch (UnknownOutboxEventTypeException ex) {
            markDead(event, "UNKNOWN_EVENT_TYPE", rootMessage(ex));
            return;
        } catch (JsonProcessingException ex) {
            markDead(event, "INVALID_EVENT_PAYLOAD", rootMessage(ex));
            return;
        }

        CorrelationData correlation = new CorrelationData(event.id().toString());
        try {
            rabbit.convertAndSend(
                    route.exchange(),
                    route.routingKey(),
                    payload,
                    message -> {
                        message.getMessageProperties().setMessageId(event.id().toString());
                        message.getMessageProperties().setHeader("x-event-id", event.id().toString());
                        message.getMessageProperties().setHeader("x-event-type", event.eventType());
                        if (event.tenantId() != null) {
                            message.getMessageProperties()
                                    .setHeader("x-tenant-id", event.tenantId().toString());
                        }
                        message.getMessageProperties()
                                .setHeader("x-aggregate-id", event.aggregateId().toString());
                        message.getMessageProperties()
                                .setHeader("x-aggregate-type", event.aggregateType());
                        return message;
                    },
                    correlation);

            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);

            if (correlation.getReturned() != null) {
                transientFailure(event, "BROKER_RETURNED", "RabbitMQ returned the message as unroutable");
            } else if (confirm.isAck()) {
                if (states.markPublished(event.id(), workerId, Instant.now(clock))) {
                    metrics.published();
                }
            } else {
                transientFailure(
                        event,
                        "BROKER_NACK",
                        confirm.getReason() == null
                                ? "RabbitMQ negatively acknowledged publish"
                                : confirm.getReason());
            }
        } catch (TimeoutException ex) {
            transientFailure(event, "BROKER_CONFIRM_TIMEOUT", rootMessage(ex));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            transientFailure(event, "BROKER_PUBLISH_INTERRUPTED", rootMessage(ex));
        } catch (Exception ex) {
            transientFailure(event, "BROKER_PUBLISH_FAILED", rootMessage(ex));
        }
    }

    private void transientFailure(
            OutboxStateService.PublishableEvent event, String code, String message) {
        if (event.attemptCount() >= retryPolicy.maxAttempts()) {
            markDead(event, "BROKER_MAX_ATTEMPTS", code + ": " + message);
            return;
        }
        Instant nextAttemptAt =
                Instant.now(clock).plus(retryPolicy.delayForAttempt(event.attemptCount()));
        if (states.scheduleRetry(event.id(), workerId, nextAttemptAt, code, message)) {
            metrics.retry();
        }
    }

    private void markDead(
            OutboxStateService.PublishableEvent event, String code, String message) {
        if (states.markDead(event.id(), workerId, code, message)) {
            metrics.dead();
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        if (message == null || message.isBlank()) message = current.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
