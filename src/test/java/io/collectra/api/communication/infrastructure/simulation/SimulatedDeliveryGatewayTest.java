package io.collectra.api.communication.infrastructure.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.communication.application.DeliveryCommand;
import io.collectra.api.communication.application.DeliveryFailureKind;
import io.collectra.api.communication.application.DeliveryResult;
import io.collectra.api.communication.domain.CommunicationChannel;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulatedDeliveryGatewayTest {
    private final SimulatedDeliveryGateway gateway = new SimulatedDeliveryGateway(80, 10);

    @Test
    void producesExactDeterministicEightyTenTenDistribution() {
        int accepted = 0;
        int permanent = 0;
        int retryable = 0;

        for (int bucket = 0; bucket < 100; bucket++) {
            DeliveryResult result =
                    gateway.deliver(command(new UUID(0, bucket), CommunicationChannel.EMAIL));
            if (result instanceof DeliveryResult.Accepted) {
                accepted++;
            } else if (((DeliveryResult.Rejected) result).kind() == DeliveryFailureKind.PERMANENT) {
                permanent++;
            } else {
                retryable++;
            }
        }

        assertThat(accepted).isEqualTo(80);
        assertThat(permanent).isEqualTo(10);
        assertThat(retryable).isEqualTo(10);
    }

    @Test
    void sameMessageAlwaysHasSameOutcome() {
        DeliveryCommand command = command(new UUID(0, 95), CommunicationChannel.SMS);

        assertThat(gateway.deliver(command)).isEqualTo(gateway.deliver(command));
    }

    @Test
    void supportsEveryCurrentChannel() {
        for (CommunicationChannel channel : EnumSet.allOf(CommunicationChannel.class)) {
            assertThat(gateway.deliver(command(new UUID(0, 1), channel)))
                    .isInstanceOf(DeliveryResult.Accepted.class);
        }
    }

    private DeliveryCommand command(UUID messageId, CommunicationChannel channel) {
        return new DeliveryCommand(
                messageId,
                UUID.randomUUID(),
                channel,
                "destination@example.com",
                channel == CommunicationChannel.EMAIL ? "Subject" : null,
                "Body");
    }
}
