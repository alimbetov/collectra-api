package io.collectra.api.communication.infrastructure.simulation;

import io.collectra.api.communication.application.DeliveryCommand;
import io.collectra.api.communication.application.DeliveryFailureKind;
import io.collectra.api.communication.application.DeliveryGateway;
import io.collectra.api.communication.application.DeliveryResult;
import io.collectra.api.communication.domain.CommunicationChannel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "collectra.communication.delivery.provider",
        havingValue = "simulated")
public class SimulatedDeliveryGateway implements DeliveryGateway {
    private final int successRate;
    private final int permanentFailureRate;

    public SimulatedDeliveryGateway(
            @Value("${collectra.communication.delivery.simulation.success-rate-percent:80}")
                    int successRate,
            @Value(
                            "${collectra.communication.delivery.simulation.permanent-failure-rate-percent:10}")
                    int permanentFailureRate) {
        if (successRate < 0
                || permanentFailureRate < 0
                || successRate + permanentFailureRate > 100) {
            throw new IllegalArgumentException("simulation rates must be between 0 and 100");
        }
        this.successRate = successRate;
        this.permanentFailureRate = permanentFailureRate;
    }

    @Override
    public DeliveryResult deliver(DeliveryCommand command) {
        DeliveryResult validation = validate(command);
        if (validation != null) {
            return validation;
        }

        int bucket = Math.floorMod(command.messageId().hashCode(), 100);
        if (bucket < successRate) {
            return new DeliveryResult.Accepted(
                    "simulated:"
                            + command.channel().name().toLowerCase()
                            + ":"
                            + command.messageId());
        }
        if (bucket < successRate + permanentFailureRate) {
            return new DeliveryResult.Rejected(
                    DeliveryFailureKind.PERMANENT,
                    "SIMULATED_PERMANENT_FAILURE",
                    "Simulated permanent provider rejection");
        }
        return new DeliveryResult.Rejected(
                DeliveryFailureKind.RETRYABLE,
                "SIMULATED_TRANSIENT_FAILURE",
                "Simulated transient provider failure");
    }

    private DeliveryResult validate(DeliveryCommand command) {
        if (command == null
                || command.messageId() == null
                || command.tenantId() == null
                || command.channel() == null) {
            return rejected(
                    "INVALID_DELIVERY_COMMAND", "Delivery identifiers and channel are required");
        }
        if (!hasText(command.destination())) {
            return rejected("INVALID_DESTINATION", "Destination is required");
        }
        if (!hasText(command.body())) {
            return rejected("INVALID_CONTENT", "Body is required");
        }
        if (command.channel() == CommunicationChannel.EMAIL && !hasText(command.subject())) {
            return rejected("INVALID_CONTENT", "Email subject is required");
        }
        return null;
    }

    private DeliveryResult rejected(String code, String message) {
        return new DeliveryResult.Rejected(DeliveryFailureKind.PERMANENT, code, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
