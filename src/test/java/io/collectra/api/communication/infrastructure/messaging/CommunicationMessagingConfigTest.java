package io.collectra.api.communication.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommunicationMessagingConfigTest {
    private final CommunicationMessagingConfig config = new CommunicationMessagingConfig();

    @Test
    void deliveryQueueUsesDedicatedDeadLetterTopologyWithoutBusinessRetryTtl() {
        var queue = config.communicationMessageDeliveryQueue();

        assertThat(queue.getName()).isEqualTo(CommunicationMessagingConfig.MESSAGE_DELIVERY_QUEUE);
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", CommunicationMessagingConfig.DEAD_EXCHANGE)
                .containsEntry(
                        "x-dead-letter-routing-key", CommunicationMessagingConfig.DEAD_ROUTING_KEY)
                .doesNotContainKeys("x-message-ttl");

        var binding =
                config.communicationMessageDeliveryBinding(queue, config.communicationExchange());
        assertThat(binding.getExchange()).isEqualTo(CommunicationMessagingConfig.EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(CommunicationMessagingConfig.ROUTING_KEY);
    }

    @Test
    void deadQueueIsDurableAndBoundToDedicatedExchange() {
        var queue = config.communicationMessageDeliveryDeadQueue();
        var binding =
                config.communicationMessageDeliveryDeadBinding(
                        queue, config.communicationDeadExchange());

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getName()).isEqualTo(CommunicationMessagingConfig.DEAD_QUEUE);
        assertThat(binding.getExchange()).isEqualTo(CommunicationMessagingConfig.DEAD_EXCHANGE);
        assertThat(binding.getRoutingKey())
                .isEqualTo(CommunicationMessagingConfig.DEAD_ROUTING_KEY);
    }
}
