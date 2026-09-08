package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.domain.GenerationJobStatus;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.MappingRule;
import io.collectra.api.importing.domain.SourceField;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.MappingRuleRepository;
import io.collectra.api.importing.infrastructure.SourceFieldRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class TemplateMappingDomainIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired FieldDefinitionRepository fields;
    @Autowired SourceSchemaRepository schemas;
    @Autowired SourceFieldRepository sourceFields;
    @Autowired MappingProfileRepository profiles;
    @Autowired MappingRuleRepository rules;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired GenerationJobRepository jobs;
    @Autowired ObjectMapper json;

    @Test
    @Transactional
    void persistsCompleteMappingTemplateAndGenerationSnapshot() {
        Tenant tenant = tenants.saveAndFlush(new Tenant("domain-" + UUID.randomUUID(), "Domain Test"));
        FieldDefinition field = fields.saveAndFlush(new FieldDefinition(tenant.getId(),
                "custom.invoice.region", "Регион", FieldDataType.STRING, "INVOICE",
                false, false, json.createObjectNode()));
        SourceSchema schema = schemas.saveAndFlush(new SourceSchema(tenant.getId(),
                "INVOICE_EXCEL", "Счета Excel", SourceFormat.EXCEL, 1));
        SourceField source = sourceFields.saveAndFlush(new SourceField(schema.getId(),
                "Регион", "STRING", "Алматы", false, 1));
        MappingProfile profile = profiles.saveAndFlush(new MappingProfile(tenant.getId(),
                schema.getId(), "INVOICE_EXCEL_RU", "Маппинг счетов", "INVOICE", 1));
        rules.saveAndFlush(new MappingRule(profile.getId(), source.getId(), field.getId(),
                json.createObjectNode().put("type", "TRIM"), null, false));
        DocumentTemplate template = templates.saveAndFlush(new DocumentTemplate(tenant.getId(),
                "INVOICE_DEFAULT", "Счёт", "INVOICE"));
        TemplateVersion version = versions.saveAndFlush(new TemplateVersion(template.getId(), 1,
                "ru-KZ", "<p>{{custom.invoice.region}}</p>", "body { font-family: sans-serif; }"));
        var payload = json.createObjectNode();
        payload.withObject("custom").withObject("invoice").put("region", "Алматы");
        GenerationJob job = jobs.saveAndFlush(new GenerationJob(tenant.getId(), "INVOICE",
                profile.getId(), version.getId(), null, payload));

        assertThat(fields.findAvailable(tenant.getId())).extracting(FieldDefinition::getKey)
                .contains("custom.invoice.region");
        assertThat(rules.findAllByMappingProfileId(profile.getId())).hasSize(1);
        assertThat(versions.findAllByTemplateIdOrderByTemplateVersionDesc(template.getId()))
                .singleElement().extracting(TemplateVersion::getContentHtml)
                .isEqualTo("<p>{{custom.invoice.region}}</p>");
        GenerationJob stored = jobs.findByIdAndTenantId(job.getId(), tenant.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(GenerationJobStatus.PENDING);
        assertThat(stored.getNormalizedPayload().at("/custom/invoice/region").asText())
                .isEqualTo("Алматы");
    }
}
