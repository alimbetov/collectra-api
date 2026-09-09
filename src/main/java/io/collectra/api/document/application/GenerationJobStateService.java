package io.collectra.api.document.application;

import com.fasterxml.jackson.databind.JsonNode;

import io.collectra.api.document.domain.GenerationJobStatus;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GenerationJobRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class GenerationJobStateService {
    private final GenerationJobRepository jobs;

    public GenerationJobStateService(GenerationJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public Snapshot begin(UUID jobId) {
        var job = jobs.findLockedById(jobId).orElseThrow();
        if (job.getStatus() == GenerationJobStatus.COMPLETED
                || job.getStatus() == GenerationJobStatus.FAILED) return null;
        if (job.getStatus() == GenerationJobStatus.PROCESSING)
            throw new IllegalStateException("Generation job is already processing");
        job.start("RENDER_HTML");
        return new Snapshot(
                job.getId(),
                job.getTenantId(),
                job.getTemplateVersionId(),
                job.getNormalizedPayload().deepCopy(),
                job.getOutputFormats());
    }

    @Transactional
    public void step(UUID jobId, String step) {
        jobs.findLockedById(jobId).orElseThrow().step(step);
    }

    @Transactional
    public void complete(UUID jobId) {
        jobs.findLockedById(jobId).orElseThrow().complete();
    }

    @Transactional
    public void retry(UUID jobId, String code, String message) {
        jobs.findLockedById(jobId).orElseThrow().retry(code, limit(message));
    }

    @Transactional
    public void fail(UUID jobId, String code, String message) {
        jobs.findLockedById(jobId).orElseThrow().fail(code, limit(message));
    }

    private String limit(String message) {
        if (message == null) return null;
        return message.substring(0, Math.min(message.length(), 1000));
    }

    public record Snapshot(
            UUID jobId,
            UUID tenantId,
            UUID templateVersionId,
            JsonNode payload,
            Set<OutputFormat> formats) {}
}
