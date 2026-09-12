package io.collectra.api.communication.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.collectra.api.communication.application.DeliveryErrorSummary;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class Slice10aP10ObservabilityContractTest {

    @Test
    void deliveryMetricsUseOnlyBoundedLabelsAndNormalizedErrorCodes() {
        var registry = new SimpleMeterRegistry();
        var metrics = new MessageDeliveryMetrics(registry, new DeliveryErrorSummary());
        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        metrics.onOutcome(
                new DeliveryOutcomeEvent(
                        tenantId,
                        campaignId,
                        runId,
                        messageId,
                        CommunicationChannel.EMAIL,
                        2,
                        DeliveryOutcomeEvent.Outcome.RETRY_SCHEDULED,
                        "upstream timeout for secret@example.test message=" + messageId,
                        Instant.parse("2026-09-12T18:00:00Z"),
                        Instant.parse("2026-09-12T18:00:05Z")));

        assertThat(registry.getMeters()).isNotEmpty();
        registry.getMeters()
                .forEach(
                        meter -> {
                            Set<String> keys =
                                    meter.getId().getTags().stream()
                                            .map(tag -> tag.getKey())
                                            .collect(java.util.stream.Collectors.toSet());
                            assertThat(keys)
                                    .isSubsetOf("channel", "result", "error_code")
                                    .doesNotContain(
                                            "tenantId",
                                            "campaignId",
                                            "campaignRunId",
                                            "messageId",
                                            "customerId",
                                            "destination",
                                            "providerReference");
                            meter.getId()
                                    .getTags()
                                    .forEach(
                                            tag -> {
                                                assertThat(tag.getValue())
                                                        .doesNotContain("secret@example.test")
                                                        .doesNotContain(messageId.toString())
                                                        .doesNotContain(tenantId.toString());
                                            });
                        });

        assertThat(
                        registry
                                .get("collectra_message_retry_total")
                                .tag("channel", "email")
                                .tag("error_code", "CONNECTION_TIMEOUT")
                                .counter()
                                .count())
                .isEqualTo(1.0d);
    }

    @Test
    void deliveryOutcomeLoggerEmitsOnlyStructuredIdentifiersAndNormalizedFailureCode() {
        Logger logger = (Logger) LoggerFactory.getLogger(DeliveryOutcomeLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);

        try {
            UUID tenantId = UUID.randomUUID();
            UUID campaignId = UUID.randomUUID();
            UUID runId = UUID.randomUUID();
            UUID messageId = UUID.randomUUID();
            String rawProviderError =
                    "timeout Authorization: Bearer super-secret-token recipient=secret@example.test body=PRIVATE";

            var outcomeLogger = new DeliveryOutcomeLogger(new DeliveryErrorSummary());
            outcomeLogger.onOutcome(
                    new DeliveryOutcomeEvent(
                            tenantId,
                            campaignId,
                            runId,
                            messageId,
                            CommunicationChannel.EMAIL,
                            1,
                            DeliveryOutcomeEvent.Outcome.RETRY_SCHEDULED,
                            rawProviderError,
                            Instant.parse("2026-09-12T18:00:00Z"),
                            Instant.parse("2026-09-12T18:00:02Z")));

            assertThat(appender.list).hasSize(1);
            String log = appender.list.get(0).getFormattedMessage();
            assertThat(log)
                    .contains("CONNECTION_TIMEOUT")
                    .contains(messageId.toString())
                    .doesNotContain("super-secret-token")
                    .doesNotContain("secret@example.test")
                    .doesNotContain("PRIVATE")
                    .doesNotContain("Authorization:");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }
}
