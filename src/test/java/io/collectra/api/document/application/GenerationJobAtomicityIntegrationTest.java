package io.collectra.api.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import io.collectra.api.importing.application.MappingExecutionService;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.shared.outbox.OutboxService;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class GenerationJobAtomicityIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenants;
    @Autowired SourceSchemaRepository schemas;
    @Autowired MappingProfileRepository profiles;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired GenerationJobRepository jobs;
    @Autowired GenerationJobService generation;
    @Autowired ObjectMapper json;

    @MockitoBean OutboxService outbox;

    @Test
    void outboxFailureRollsBackGenerationJob() {
        Tenant tenant = tenants.saveAndFlush(new Tenant("atomic-" + UUID.randomUUID(), "Atomic"));
        SourceSchema schema = new SourceSchema(
                tenant.getId(), "ATOMIC_JSON", "Atomic JSON", SourceFormat.JSON, 1);
        schema.validated();
        schema.publish();
        schemas.saveAndFlush(schema);
        MappingProfile profile = new MappingProfile(
                tenant.getId(), schema.getId(), "ATOMIC_JSON", "Atomic JSON", "INVOICE", 1);
        profile.validated();
        profile.publish();
        profiles.saveAndFlush(profile);
        DocumentTemplate template = templates.saveAndFlush(
                new DocumentTemplate(tenant.getId(), "ATOMIC_TEMPLATE", "Atomic", "INVOICE"));
        TemplateVersion version = new TemplateVersion(
                template.getId(), 1, "en", "<p>{{custom.invoice.number}}</p>", null);
        version.validated();
        version.publish();
        versions.saveAndFlush(version);
        var payload = json.createObjectNode();
        payload.putObject("custom").putObject("invoice").put("number", "INV-1");
        var mapped = new MappingExecutionService.MappingResult(
                profile.getId(), schema.getId(), "INVOICE", payload, "mapping-sha");
        long before = jobs.count();
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outbox)
                .append(any(), anyString(), any(), anyString(), anyString());

        assertThatThrownBy(() -> generation.createMapped(
                        tenant.getId(),
                        profile.getId(),
                        version.getId(),
                        mapped,
                        Set.of(OutputFormat.HTML)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");

        assertThat(jobs.count()).isEqualTo(before);
    }
}
