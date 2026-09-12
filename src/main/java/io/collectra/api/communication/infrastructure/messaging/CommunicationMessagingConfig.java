package io.collectra.api.communication.infrastructure.messaging;

import io.collectra.api.document.infrastructure.DocumentMessagingConfig;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CommunicationMessagingConfig {
    public static final String EXCHANGE = "collectra.communication";
    public static final String MESSAGE_DELIVERY_QUEUE = "collectra.communication.message-delivery";
    public static final String ROUTING_KEY = "message.delivery.requested";
    public static final String DEAD_EXCHANGE = "collectra.communication.dead";
    public static final String DEAD_QUEUE = "collectra.communication.message-delivery.dead";
    public static final String DEAD_ROUTING_KEY = "message.delivery.dead";
    public static final String DOCUMENT_COMPLETED_QUEUE =
            "collectra.communication.document-generation-completed";
    public static final String DOCUMENT_FAILED_QUEUE =
            "collectra.communication.document-generation-failed";

    @Bean
    DirectExchange communicationExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange communicationDeadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    Queue communicationMessageDeliveryQueue() {
        return new Queue(
                MESSAGE_DELIVERY_QUEUE,
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange",
                        DEAD_EXCHANGE,
                        "x-dead-letter-routing-key",
                        DEAD_ROUTING_KEY));
    }

    @Bean
    Binding communicationMessageDeliveryBinding(
            @Qualifier("communicationMessageDeliveryQueue") Queue queue,
            @Qualifier("communicationExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    Queue communicationMessageDeliveryDeadQueue() {
        return new Queue(DEAD_QUEUE, true);
    }

    @Bean
    Binding communicationMessageDeliveryDeadBinding(
            @Qualifier("communicationMessageDeliveryDeadQueue") Queue queue,
            @Qualifier("communicationDeadExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(DEAD_ROUTING_KEY);
    }

    @Bean
    Queue communicationDocumentCompletedQueue() {
        return new Queue(DOCUMENT_COMPLETED_QUEUE, true);
    }

    @Bean
    Queue communicationDocumentFailedQueue() {
        return new Queue(DOCUMENT_FAILED_QUEUE, true);
    }

    @Bean
    Binding communicationDocumentCompletedBinding(
            @Qualifier("communicationDocumentCompletedQueue") Queue queue,
            @Qualifier("documentExchange") DirectExchange documentExchange) {
        return BindingBuilder.bind(queue)
                .to(documentExchange)
                .with(DocumentMessagingConfig.COMPLETED_ROUTING_KEY);
    }

    @Bean
    Binding communicationDocumentFailedBinding(
            @Qualifier("communicationDocumentFailedQueue") Queue queue,
            @Qualifier("documentExchange") DirectExchange documentExchange) {
        return BindingBuilder.bind(queue)
                .to(documentExchange)
                .with(DocumentMessagingConfig.FAILED_ROUTING_KEY);
    }
}
