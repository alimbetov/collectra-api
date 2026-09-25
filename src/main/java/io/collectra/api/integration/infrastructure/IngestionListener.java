package io.collectra.api.integration.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.integration.application.*;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class IngestionListener {
    private final IngestionWorker worker;
    private final RabbitTemplate rabbit;
    public IngestionListener(IngestionWorker worker,RabbitTemplate rabbit){this.worker=worker;this.rabbit=rabbit;}

    @RabbitListener(queues=IngestionMessagingConfig.QUEUE)
    public void consume(JsonNode payload,Message message){
        try {
            UUID tenantId=required(payload,"tenantId"); UUID ingestionId=required(payload,"ingestionId");
            worker.process(tenantId,ingestionId);
        } catch(IngestionRetryableException ex) {
            int retry=header(message,"x-retry-count");
            if(retry>=3){dead(payload,retry);return;}
            String delay=retry==0?"1m":retry==1?"10m":"1h";
            rabbit.convertAndSend(IngestionMessagingConfig.RETRY_EXCHANGE,delay,payload,m->{m.getMessageProperties().setHeader("x-retry-count",retry+1);return m;});
        } catch(RuntimeException poison) {
            dead(payload,header(message,"x-retry-count"));
        }
    }
    private void dead(JsonNode payload,int retry){rabbit.convertAndSend(IngestionMessagingConfig.EXCHANGE,"integration.ingestion.dead",payload,m->{m.getMessageProperties().setHeader("x-retry-count",retry);return m;});}
    private int header(Message m,String name){Object v=m.getMessageProperties().getHeader(name);return v instanceof Number n?n.intValue():0;}
    private UUID required(JsonNode p,String name){String v=p.path(name).asText(null);if(v==null||v.isBlank())throw new IllegalArgumentException(name+" is required");return UUID.fromString(v);}
}
