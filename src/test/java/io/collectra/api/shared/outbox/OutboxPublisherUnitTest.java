package io.collectra.api.shared.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OutboxPublisherUnitTest {
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    private OutboxClaimService claims;
    private OutboxStateService states;
    private OutboxEventRouter router;
    private OutboxMetrics metrics;
    private RabbitTemplate rabbit;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        claims = mock(OutboxClaimService.class);
        states = mock(OutboxStateService.class);
        router = mock(OutboxEventRouter.class);
        metrics = mock(OutboxMetrics.class);
        rabbit = mock(RabbitTemplate.class);
        publisher =
                new OutboxPublisher(
                        claims,
                        states,
                        router,
                        new OutboxRetryPolicy(3),
                        metrics,
                        rabbit,
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        100,
                        50,
                        Duration.ofMinutes(2));
    }

    @Test
    void ackMarksPublishedAndRecordsMetric() {
        var event = event(1, "{\"jobId\":\"123\"}");
        when(states.loadForPublish(eq(event.id()), anyString())).thenReturn(Optional.of(event));
        when(router.route(event.eventType())).thenReturn(new OutboxRoute("documents", "generate"));
        when(states.markPublished(eq(event.id()), anyString(), eq(NOW))).thenReturn(true);
        completePublishWithAck(true, null);

        publisher.publishOne(event.id());

        verify(states).markPublished(eq(event.id()), anyString(), eq(NOW));
        verify(metrics).published();
    }

    @Test
    void invalidPayloadMovesEventToDeadWithoutPublishing() {
        var event = event(1, "not-json");
        when(states.loadForPublish(eq(event.id()), anyString())).thenReturn(Optional.of(event));
        when(router.route(event.eventType())).thenReturn(new OutboxRoute("documents", "generate"));
        when(states.markDead(eq(event.id()), anyString(), eq("INVALID_EVENT_PAYLOAD"), anyString()))
                .thenReturn(true);

        publisher.publishOne(event.id());

        verify(states)
                .markDead(eq(event.id()), anyString(), eq("INVALID_EVENT_PAYLOAD"), anyString());
        verify(metrics).dead();
        verifyNoInteractions(rabbit);
    }

    @Test
    void nackSchedulesRetryBeforeAttemptLimit() {
        var event = event(1, "{\"jobId\":\"123\"}");
        when(states.loadForPublish(eq(event.id()), anyString())).thenReturn(Optional.of(event));
        when(router.route(event.eventType())).thenReturn(new OutboxRoute("documents", "generate"));
        when(states.scheduleRetry(
                        eq(event.id()), anyString(), eq(NOW), eq("BROKER_NACK"), eq("nack")))
                .thenReturn(true);
        completePublishWithAck(false, "nack");

        publisher.publishOne(event.id());

        verify(states)
                .scheduleRetry(eq(event.id()), anyString(), eq(NOW), eq("BROKER_NACK"), eq("nack"));
        verify(metrics).retry();
        verify(metrics, never()).dead();
    }

    @Test
    void nackAtAttemptLimitMovesEventToDead() {
        var event = event(3, "{\"jobId\":\"123\"}");
        when(states.loadForPublish(eq(event.id()), anyString())).thenReturn(Optional.of(event));
        when(router.route(event.eventType())).thenReturn(new OutboxRoute("documents", "generate"));
        when(states.markDead(eq(event.id()), anyString(), eq("BROKER_MAX_ATTEMPTS"), anyString()))
                .thenReturn(true);
        completePublishWithAck(false, "nack");

        publisher.publishOne(event.id());

        verify(states)
                .markDead(eq(event.id()), anyString(), eq("BROKER_MAX_ATTEMPTS"), anyString());
        verify(states, never()).scheduleRetry(any(), anyString(), any(), anyString(), anyString());
        verify(metrics).dead();
    }

    private void completePublishWithAck(boolean ack, String reason) {
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(4);
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(ack, reason));
                            return null;
                        })
                .when(rabbit)
                .convertAndSend(
                        anyString(),
                        anyString(),
                        any(),
                        any(MessagePostProcessor.class),
                        any(CorrelationData.class));
    }

    private OutboxStateService.PublishableEvent event(int attemptCount, String payload) {
        return new OutboxStateService.PublishableEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "GENERATION_JOB",
                UUID.randomUUID(),
                "DOCUMENT_GENERATION_REQUESTED",
                payload,
                attemptCount);
    }
}
