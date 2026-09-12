package io.collectra.api.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.domain.GenerationJobStatus;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GenerationJobStateConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired GenerationJobStateService states;
    @Autowired GenerationJobRepository jobs;
    @Autowired TenantRepository tenants;
    @Autowired SourceSchemaRepository schemas;
    @Autowired MappingProfileRepository profiles;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired ObjectMapper json;

    @Test
    void concurrentWorkersCannotBothClaimSameGenerationJob() throws Exception {
        GenerationJob job = createPendingJob();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> claim =
                () -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("generation race did not start");
                    }
                    try {
                        return states.begin(job.getTenantId(), job.getId());
                    } catch (IllegalStateException alreadyProcessing) {
                        return alreadyProcessing;
                    }
                };

        try {
            Future<Object> first = executor.submit(claim);
            Future<Object> second = executor.submit(claim);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = new ArrayList<>();
            results.add(first.get(10, TimeUnit.SECONDS));
            results.add(second.get(10, TimeUnit.SECONDS));

            assertThat(
                            results.stream()
                                    .filter(GenerationJobStateService.Snapshot.class::isInstance)
                                    .count())
                    .isEqualTo(1);
            assertThat(results.stream().filter(IllegalStateException.class::isInstance).count())
                    .isEqualTo(1);
            assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                    .isEqualTo(GenerationJobStatus.PROCESSING);
        } finally {
            executor.shutdownNow();
        }
    }

    private GenerationJob createPendingJob() {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("generation-race-" + UUID.randomUUID(), "Generation Race"));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SourceSchema schema =
                schemas.saveAndFlush(
                        new SourceSchema(
                                tenant.getId(),
                                "GEN_" + suffix,
                                "Generation Schema",
                                SourceFormat.JSON,
                                1));
        MappingProfile profile =
                profiles.saveAndFlush(
                        new MappingProfile(
                                tenant.getId(),
                                schema.getId(),
                                "GEN_" + suffix,
                                "Generation Profile",
                                "INVOICE",
                                1));
        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(), "GEN_" + suffix, "Generation Template", "INVOICE"));
        TemplateVersion version =
                versions.saveAndFlush(
                        new TemplateVersion(template.getId(), 1, "en", "<p>generation</p>", null));
        return jobs.saveAndFlush(
                new GenerationJob(
                        tenant.getId(),
                        "INVOICE",
                        profile.getId(),
                        version.getId(),
                        null,
                        json.createObjectNode(),
                        Set.of(OutputFormat.HTML)));
    }
}
