package io.collectra.api.document.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentMessagingConfig {
    public static final String EXCHANGE = "collectra.documents";
    public static final String QUEUE = "collectra.document-generation";
    public static final String ROUTING_KEY = "generation.requested";
    public static final String COMPLETED_ROUTING_KEY = "generation.completed";
    public static final String FAILED_ROUTING_KEY = "generation.failed";
    public static final String RETRY_EXCHANGE = "collectra.documents.retry";
    public static final String DEAD_QUEUE = "collectra.document-generation.dead";

    @Bean
    DirectExchange documentExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange documentRetryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    Queue documentQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Binding documentBinding(Queue documentQueue, DirectExchange documentExchange) {
        return BindingBuilder.bind(documentQueue).to(documentExchange).with(ROUTING_KEY);
    }

    @Bean
    Queue documentDeadQueue() {
        return new Queue(DEAD_QUEUE, true);
    }

    @Bean
    Binding documentDeadBinding(Queue documentDeadQueue, DirectExchange documentExchange) {
        return BindingBuilder.bind(documentDeadQueue).to(documentExchange).with("generation.dead");
    }

    @Bean
    Queue retryOneMinute() {
        return retryQueue("collectra.document-generation.retry.1m", 60_000);
    }

    @Bean
    Queue retryTenMinutes() {
        return retryQueue("collectra.document-generation.retry.10m", 600_000);
    }

    @Bean
    Queue retryOneHour() {
        return retryQueue("collectra.document-generation.retry.1h", 3_600_000);
    }

    @Bean
    Binding retryOneMinuteBinding(Queue retryOneMinute, DirectExchange documentRetryExchange) {
        return BindingBuilder.bind(retryOneMinute).to(documentRetryExchange).with("1m");
    }

    @Bean
    Binding retryTenMinutesBinding(Queue retryTenMinutes, DirectExchange documentRetryExchange) {
        return BindingBuilder.bind(retryTenMinutes).to(documentRetryExchange).with("10m");
    }

    @Bean
    Binding retryOneHourBinding(Queue retryOneHour, DirectExchange documentRetryExchange) {
        return BindingBuilder.bind(retryOneHour).to(documentRetryExchange).with("1h");
    }

    @Bean
    Jackson2JsonMessageConverter rabbitJsonConverter(ObjectMapper json) {
        return new Jackson2JsonMessageConverter(json);
    }

    private Queue retryQueue(String name, int ttl) {
        return new Queue(
                name,
                true,
                false,
                false,
                Map.of(
                        "x-message-ttl", ttl,
                        "x-dead-letter-exchange", EXCHANGE,
                        "x-dead-letter-routing-key", ROUTING_KEY));
    }
}
