package io.collectra.api.integration.infrastructure;
import org.springframework.amqp.core.*;import org.springframework.context.annotation.*;
@Configuration public class IngestionMessagingConfig{
 public static final String EXCHANGE="collectra.integration",QUEUE="collectra.integration-ingestion",ROUTING_KEY="integration.ingestion.requested";
 @Bean DirectExchange integrationExchange(){return new DirectExchange(EXCHANGE,true,false);}@Bean Queue integrationIngestionQueue(){return new Queue(QUEUE,true);}@Bean Binding integrationIngestionBinding(Queue integrationIngestionQueue,DirectExchange integrationExchange){return BindingBuilder.bind(integrationIngestionQueue).to(integrationExchange).with(ROUTING_KEY);}
}
