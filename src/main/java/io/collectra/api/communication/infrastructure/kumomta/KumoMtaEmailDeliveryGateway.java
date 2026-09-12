package io.collectra.api.communication.infrastructure.kumomta;

import io.collectra.api.communication.application.DeliveryCommand;
import io.collectra.api.communication.application.DeliveryFailureKind;
import io.collectra.api.communication.application.DeliveryGateway;
import io.collectra.api.communication.application.DeliveryResult;
import io.collectra.api.communication.domain.CommunicationChannel;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "collectra.communication.delivery.provider", havingValue = "kumomta")
public class KumoMtaEmailDeliveryGateway implements DeliveryGateway {
    private static final Logger log = LoggerFactory.getLogger(KumoMtaEmailDeliveryGateway.class);

    private final KumoMtaClient client;
    private final KumoMtaProperties properties;
    private final KumoMtaErrorClassifier errors;

    public KumoMtaEmailDeliveryGateway(
            KumoMtaClient client, KumoMtaProperties properties, KumoMtaErrorClassifier errors) {
        this.client = client;
        this.properties = properties;
        this.errors = errors;
    }

    @Override
    public DeliveryResult deliver(DeliveryCommand command) {
        DeliveryResult validation = validate(command);
        if (validation != null) {
            return validation;
        }

        long started = System.nanoTime();
        try {
            KumoMtaInjectResponse response = client.inject(toRequest(command));
            if (response != null && response.acceptedSingleRecipient()) {
                log.info(
                        "KumoMTA injection accepted tenantId={} messageId={} latencyMs={}",
                        command.tenantId(),
                        command.messageId(),
                        elapsedMillis(started));
                return new DeliveryResult.Accepted(null);
            }
            return reject(command, errors.classifyResponse(response), started);
        } catch (RuntimeException failure) {
            return reject(command, errors.classify(failure), started);
        }
    }

    private KumoMtaInjectRequest toRequest(DeliveryCommand command) {
        return new KumoMtaInjectRequest(
                properties.getEnvelopeSender().trim(),
                new KumoMtaInjectRequest.Content(
                        Map.of(
                                "From",
                                properties.effectiveFromHeader(),
                                "Subject",
                                command.subject()),
                        command.body()),
                List.of(
                        new KumoMtaRecipient(
                                command.destination().trim(),
                                Map.of(
                                        "collectra_message_id",
                                        command.messageId().toString(),
                                        "collectra_tenant_id",
                                        command.tenantId().toString()))),
                "Static",
                false,
                false);
    }

    private DeliveryResult validate(DeliveryCommand command) {
        if (command == null || command.messageId() == null || command.tenantId() == null) {
            return permanent("INVALID_DELIVERY_COMMAND", "Delivery identifiers are required");
        }
        if (command.channel() != CommunicationChannel.EMAIL) {
            return permanent("UNSUPPORTED_CHANNEL", "KumoMTA adapter supports EMAIL only");
        }
        if (!hasText(command.destination()) || !command.destination().contains("@")) {
            return permanent("INVALID_DESTINATION", "Email destination is invalid");
        }
        if (!hasText(command.subject()) || !hasText(command.body())) {
            return permanent("INVALID_CONTENT", "Email subject and body are required");
        }
        return null;
    }

    private DeliveryResult reject(
            DeliveryCommand command,
            KumoMtaErrorClassifier.Classification classification,
            long started) {
        log.warn(
                "KumoMTA injection rejected tenantId={} messageId={} code={} kind={} latencyMs={}",
                command.tenantId(),
                command.messageId(),
                classification.code(),
                classification.kind(),
                elapsedMillis(started));
        return new DeliveryResult.Rejected(
                classification.kind(), classification.code(), classification.message());
    }

    private DeliveryResult permanent(String code, String message) {
        return new DeliveryResult.Rejected(DeliveryFailureKind.PERMANENT, code, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
