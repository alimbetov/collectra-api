package io.collectra.api.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.domain.GenerationJobStatus;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GenerationJobStateService {
    private final GenerationJobRepository jobs;
    private final DocumentGenerationEventPublisher events;
    private final Clock clock;

    public GenerationJobStateService(
            GenerationJobRepository jobs, DocumentGenerationEventPublisher events, Clock clock) {
        this.jobs = jobs;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Snapshot begin(UUID tenantId, UUID jobId) {
        GenerationJob job = locked(tenantId, jobId);
        if (job.getStatus() == GenerationJobStatus.COMPLETED
                || job.getStatus() == GenerationJobStatus.FAILED) {
            return null;
        }
        if (job.getStatus() == GenerationJobStatus.PROCESSING) {
            throw new IllegalStateException("Generation job is already processing");
        }
        job.start("RENDER_HTML", clock.instant());
        return snapshot(job);
    }

    @Transactional
    public void step(UUID tenantId, UUID jobId, String step) {
        locked(tenantId, jobId).step(step);
    }

    @Transactional
    public boolean complete(UUID tenantId, UUID jobId) {
        GenerationJob job = locked(tenantId, jobId);
        if (job.getStatus() == GenerationJobStatus.COMPLETED) {
            return false;
        }
        if (job.getStatus() == GenerationJobStatus.FAILED) {
            return false;
        }
        job.complete(clock.instant());
        events.completed(tenantId, jobId);
        return true;
    }

    @Transactional
    public void retry(UUID tenantId, UUID jobId, String code, String message) {
        locked(tenantId, jobId).retry(code, limit(message));
    }

    @Transactional
    public boolean fail(UUID tenantId, UUID jobId, String code, String message) {
        GenerationJob job = locked(tenantId, jobId);
        if (!job.fail(code, limit(message), clock.instant())) {
            return false;
        }
        events.failed(tenantId, jobId, code);
        return true;
    }

    private GenerationJob locked(UUID tenantId, UUID jobId) {
        return jobs.findLockedByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Generation job not found"));
    }

    private Snapshot snapshot(GenerationJob job) {
        return new Snapshot(
                job.getId(),
                job.getTenantId(),
                job.getTemplateVersionId(),
                job.getNormalizedPayload().deepCopy(),
                job.getOutputFormats());
    }

    private String limit(String message) {
        if (message == null) {
            return null;
        }
        return message.substring(0, Math.min(message.length(), 1000));
    }

    public record Snapshot(
            UUID jobId,
            UUID tenantId,
            UUID templateVersionId,
            JsonNode payload,
            Set<OutputFormat> formats) {}
}
