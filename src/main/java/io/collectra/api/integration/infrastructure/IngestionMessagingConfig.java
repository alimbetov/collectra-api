package io.collectra.api.integration.infrastructure;

import java.util.Map;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class IngestionMessagingConfig {
    public static final String EXCHANGE="collectra.integration";
    public static final String QUEUE="collectra.integration-ingestion";
    public static final String ROUTING_KEY="integration.ingestion.requested";
    public static final String RETRY_EXCHANGE="collectra.integration.retry";
    public static final String DEAD_QUEUE="collectra.integration-ingestion.dead";

    @Bean DirectExchange integrationExchange(){return new DirectExchange(EXCHANGE,true,false);}
    @Bean DirectExchange integrationRetryExchange(){return new DirectExchange(RETRY_EXCHANGE,true,false);}
    @Bean Queue integrationIngestionQueue(){return new Queue(QUEUE,true);}
    @Bean Binding integrationIngestionBinding(Queue integrationIngestionQueue,DirectExchange integrationExchange){return BindingBuilder.bind(integrationIngestionQueue).to(integrationExchange).with(ROUTING_KEY);}
    @Bean Queue integrationDeadQueue(){return new Queue(DEAD_QUEUE,true);}
    @Bean Binding integrationDeadBinding(Queue integrationDeadQueue,DirectExchange integrationExchange){return BindingBuilder.bind(integrationDeadQueue).to(integrationExchange).with("integration.ingestion.dead");}
    @Bean Queue ingestionRetry1m(){return retryQueue("collectra.integration-ingestion.retry.1m",60_000);}
    @Bean Queue ingestionRetry10m(){return retryQueue("collectra.integration-ingestion.retry.10m",600_000);}
    @Bean Queue ingestionRetry1h(){return retryQueue("collectra.integration-ingestion.retry.1h",3_600_000);}
    @Bean Binding ingestionRetry1mBinding(Queue ingestionRetry1m,DirectExchange integrationRetryExchange){return BindingBuilder.bind(ingestionRetry1m).to(integrationRetryExchange).with("1m");}
    @Bean Binding ingestionRetry10mBinding(Queue ingestionRetry10m,DirectExchange integrationRetryExchange){return BindingBuilder.bind(ingestionRetry10m).to(integrationRetryExchange).with("10m");}
    @Bean Binding ingestionRetry1hBinding(Queue ingestionRetry1h,DirectExchange integrationRetryExchange){return BindingBuilder.bind(ingestionRetry1h).to(integrationRetryExchange).with("1h");}
    private Queue retryQueue(String name,int ttl){return new Queue(name,true,false,false,Map.of("x-message-ttl",ttl,"x-dead-letter-exchange",EXCHANGE,"x-dead-letter-routing-key",ROUTING_KEY));}
}
