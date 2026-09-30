package io.collectra.api.integration.infrastructure;

import io.collectra.api.integration.application.IngestionRequestPublisher;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class IngestionRecoveryScheduler {
    private final IngestionBatchRepository batches;
    private final IngestionRequestPublisher requests;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Duration processingTimeout;

    public IngestionRecoveryScheduler(IngestionBatchRepository batches,IngestionRequestPublisher requests,Clock clock,
            PlatformTransactionManager tm,@Value("${collectra.integration.processing-timeout:PT10M}") Duration processingTimeout){
        this.batches=batches;this.requests=requests;this.clock=clock;this.tx=new TransactionTemplate(tm);this.processingTimeout=processingTimeout;
    }

    @Scheduled(fixedDelayString="${collectra.integration.recovery-delay-ms:60000}")
    public void recover(){
        Instant now=clock.instant();
        for(UUID id:batches.findStale(now.minus(processingTimeout),PageRequest.of(0,50))){
            tx.executeWithoutResult(s->{var b=batches.findById(id).orElseThrow();b.recover(now);batches.saveAndFlush(b);requests.requested(b.getTenantId(),b.getId());});
        }
        for(UUID id:batches.findRetryable(now,PageRequest.of(0,50))){
            tx.executeWithoutResult(s->{var b=batches.findById(id).orElseThrow();requests.requested(b.getTenantId(),b.getId());});
        }
    }

}
