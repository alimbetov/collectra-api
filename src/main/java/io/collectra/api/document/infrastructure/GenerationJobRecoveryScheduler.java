package io.collectra.api.document.infrastructure;

import io.collectra.api.document.application.GenerationJobRecoveryProperties;
import io.collectra.api.document.application.GenerationJobStateService;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GenerationJobRecoveryScheduler {
    private final GenerationJobRepository jobs;
    private final GenerationJobStateService states;
    private final GenerationJobRecoveryProperties properties;
    private final Clock clock;

    public GenerationJobRecoveryScheduler(
            GenerationJobRepository jobs,
            GenerationJobStateService states,
            GenerationJobRecoveryProperties properties,
            Clock clock) {
        this.jobs = jobs;
        this.states = states;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${collectra.document.generation-recovery.scan-delay:1m}")
    public void recoverStaleProcessing() {
        var staleBefore = clock.instant().minus(properties.getProcessingTimeout());
        for (var jobId : jobs.findStaleProcessingIds(staleBefore, properties.getBatchSize())) {
            states.recoverStale(jobId, properties);
        }
    }
}
