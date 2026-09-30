package io.collectra.api.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.domain.GenerationJobStatus;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import io.collectra.api.shared.outbox.OutboxRepository;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GenerationJobRecoveryIntegrationTest extends AbstractIntegrationTest {
    @Autowired GenerationJobRepository jobs;
    @Autowired GenerationJobStateService states;
    @Autowired GenerationJobRecoveryProperties properties;
    @Autowired OutboxRepository outbox;
    @Autowired TenantRepository tenants;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired ObjectMapper json;

    @Test
    void staleProcessingIsRequeuedWithDurableGenerationRequest() {
        GenerationJob job = processingJob();
        ageProcessing(job, 31);
        UUID jobId = job.getId();

        var result = states.recoverStale(jobId, properties);

        assertThat(result).isEqualTo(GenerationJobStateService.RecoveryOutcome.REQUEUED);
        GenerationJob recovered = jobs.findById(job.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(GenerationJobStatus.PENDING);
        assertThat(recovered.getErrorCode()).isEqualTo("WORKER_RECOVERED");
        assertThat(outbox.findAll())
                .anySatisfy(
                        event -> {
                            assertThat(event.getAggregateId()).isEqualTo(jobId);
                            assertThat(event.getEventType())
                                    .isEqualTo(DocumentGenerationRequestPublisher.EVENT_TYPE);
                        });
    }

    @Test
    void exhaustedProcessingFailsAndPublishesDownstreamFailure() {
        GenerationJob job = processingJob();
        for (int attempt = 1; attempt < properties.getMaxAttempts(); attempt++) {
            job.retry("TEST", "retry");
            jobs.saveAndFlush(job);
            states.begin(job.getTenantId(), job.getId());
            job = jobs.findById(job.getId()).orElseThrow();
        }
        ageProcessing(job, 31);

        var result = states.recoverStale(job.getId(), properties);

        assertThat(result).isEqualTo(GenerationJobStateService.RecoveryOutcome.FAILED);
        assertThat(jobs.findById(jobId).orElseThrow().getStatus())
                .isEqualTo(GenerationJobStatus.FAILED);
        assertThat(outbox.findAll())
                .anySatisfy(
                        event -> {
                            assertThat(event.getAggregateId()).isEqualTo(job.getId());
                            assertThat(event.getEventType())
                                    .isEqualTo(DocumentGenerationEventPublisher.FAILED_EVENT_TYPE);
                        });
    }

    private GenerationJob processingJob() {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant(
                                "generation-recovery-" + UUID.randomUUID(), "Generation Recovery"));
        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(), "REC_" + UUID.randomUUID(), "Recovery", "INVOICE"));
        TemplateVersion version =
                versions.saveAndFlush(
                        new TemplateVersion(template.getId(), 1, "en", "<p>recovery</p>", null));
        GenerationJob job =
                jobs.saveAndFlush(
                        new GenerationJob(
                                tenant.getId(),
                                "INVOICE",
                                null,
                                version.getId(),
                                null,
                                json.createObjectNode(),
                                Set.of(OutputFormat.HTML)));
        states.begin(tenant.getId(), job.getId());
        return jobs.findById(job.getId()).orElseThrow();
    }

    private void ageProcessing(GenerationJob job, long minutes) {
        // Integration test uses SQL because startedAt is deliberately domain-write-only.
        jdbc.update(
                "update generation_jobs set started_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(minutes * 60)),
                job.getId());
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
}
