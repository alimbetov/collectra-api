package io.collectra.api.integration.infrastructure;

import io.collectra.api.integration.domain.IngestionBatch;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class IngestionRecoveryScheduler {
    private final IngestionBatchRepository batches;
    private final RabbitTemplate rabbit;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Duration processingTimeout;

    public IngestionRecoveryScheduler(IngestionBatchRepository batches,RabbitTemplate rabbit,Clock clock,
            PlatformTransactionManager tm,@Value("${collectra.integration.processing-timeout:PT10M}") Duration processingTimeout){
        this.batches=batches;this.rabbit=rabbit;this.clock=clock;this.tx=new TransactionTemplate(tm);this.processingTimeout=processingTimeout;
    }

    @Scheduled(fixedDelayString="${collectra.integration.recovery-delay-ms:60000}")
    public void recover(){
        Instant now=clock.instant();
        for(UUID id:batches.findStale(now.minus(processingTimeout),PageRequest.of(0,50))){
            Event event=tx.execute(s->{IngestionBatch b=batches.findById(id).orElseThrow();b.recover(now);batches.saveAndFlush(b);return new Event(b.getTenantId(),b.getId());});
            publish(event);
        }
        for(UUID id:batches.findRetryable(now,PageRequest.of(0,50))){
            IngestionBatch b=batches.findById(id).orElseThrow();
            publish(new Event(b.getTenantId(),b.getId()));
        }
    }

    private void publish(Event e){
        rabbit.convertAndSend(IngestionMessagingConfig.EXCHANGE,IngestionMessagingConfig.ROUTING_KEY,
                Map.of("tenantId",e.tenantId().toString(),"ingestionId",e.ingestionId().toString()));
    }
    private record Event(UUID tenantId,UUID ingestionId){}
}
