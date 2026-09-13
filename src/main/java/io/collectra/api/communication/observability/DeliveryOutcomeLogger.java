package io.collectra.api.communication.observability;

import io.collectra.api.communication.application.DeliveryErrorSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DeliveryOutcomeLogger {
    private static final Logger log = LoggerFactory.getLogger(DeliveryOutcomeLogger.class);

    private final DeliveryErrorSummary errorSummary;

    public DeliveryOutcomeLogger(DeliveryErrorSummary errorSummary) {
        this.errorSummary = errorSummary;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutcome(DeliveryOutcomeEvent event) {
        String normalizedErrorCode = errorSummary.normalizeCode(event.errorCode());
        String semanticEvent =
                switch (event.outcome()) {
                    case SENT -> "message_delivery_sent";
                    case FAILED -> "message_delivery_failed";
                    case RETRY_SCHEDULED -> "message_delivery_retry_scheduled";
                    case UNKNOWN -> "message_delivery_unknown";
                };

        log.info(
                "event={} tenantId={} campaignId={} campaignRunId={} messageId={} channel={} attemptCount={} errorCode={} traceId={} spanId={}",
                semanticEvent,
                event.tenantId(),
                event.campaignId(),
                event.campaignRunId(),
                event.messageId(),
                event.channel(),
                event.attemptCount(),
                normalizedErrorCode,
                MDC.get("traceId"),
                MDC.get("spanId"));
    }
}
