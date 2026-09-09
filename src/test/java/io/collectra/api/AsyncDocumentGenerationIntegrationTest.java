package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.collectra.api.document.application.DocumentGenerationWorker;
import io.collectra.api.document.application.DocumentStorage;
import io.collectra.api.document.application.GenerationJobService;
import io.collectra.api.document.domain.GenerationJobStatus;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Import(AsyncDocumentGenerationIntegrationTest.StorageConfig.class)
class AsyncDocumentGenerationIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired FieldDefinitionRepository fields;
    @Autowired SourceSchemaRepository schemas;
    @Autowired SourceFieldRepository sourceFields;
    @Autowired MappingProfileRepository profiles;
    @Autowired MappingRuleRepository rules;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired GenerationJobRepository jobs;
    @Autowired GeneratedDocumentRepository documents;
    @Autowired GenerationJobService generation;
    @Autowired DocumentGenerationWorker worker;
    @Autowired DocumentStorage storage;
    @Autowired ObjectMapper json;

    @Test
    void mapsUploadAndGeneratesIdempotentHtmlAndPdfOutputs() {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("generation-" + UUID.randomUUID(), "Generation Test"));
        FieldDefinition customer =
                fields.saveAndFlush(
                        new FieldDefinition(
                                tenant.getId(),
                                "custom.invoice.customer",
                                "Customer",
                                FieldDataType.STRING,
                                "INVOICE",
                                false,
                                true,
                                json.createObjectNode()));
        FieldDefinition total =
                fields.saveAndFlush(
                        new FieldDefinition(
                                tenant.getId(),
                                "custom.invoice.total",
                                "Total",
                                FieldDataType.DECIMAL,
                                "INVOICE",
                                false,
                                true,
                                json.createObjectNode()));

        SourceSchema schema =
                new SourceSchema(tenant.getId(), "INVOICE_CSV", "Invoice CSV", SourceFormat.CSV, 1);
        schema.validated();
        schema.publish();
        schemas.saveAndFlush(schema);
        SourceField customerSource =
                sourceFields.saveAndFlush(
                        new SourceField(schema.getId(), "Customer", "STRING", "Alpha", true, 1));
        SourceField totalSource =
                sourceFields.saveAndFlush(
                        new SourceField(schema.getId(), "Total", "DECIMAL", "1250,75", true, 2));

        MappingProfile profile =
                new MappingProfile(
                        tenant.getId(), schema.getId(), "INVOICE_CSV", "Invoice CSV", "INVOICE", 1);
        profile.validated();
        profile.publish();
        profiles.saveAndFlush(profile);
        rules.saveAndFlush(
                new MappingRule(
                        profile.getId(),
                        customerSource.getId(),
                        customer.getId(),
                        json.createObjectNode().put("type", "TRIM"),
                        null,
                        true));
        rules.saveAndFlush(
                new MappingRule(
                        profile.getId(),
                        totalSource.getId(),
                        total.getId(),
                        json.createObjectNode()
                                .put("type", "DECIMAL_PARSE")
                                .put("decimalSeparator", ","),
                        null,
                        true));

        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(tenant.getId(), "INVOICE", "Invoice", "INVOICE"));
        TemplateVersion version =
                new TemplateVersion(
                        template.getId(),
                        1,
                        "en",
                        "<h1>{{custom.invoice.customer}}</h1><p>{{custom.invoice.total}}</p>",
                        "body { font-family: sans-serif; }");
        version.validated();
        version.publish();
        versions.saveAndFlush(version);

        var job =
                generation.create(
                        tenant.getId(),
                        profile.getId(),
                        version.getId(),
                        "Customer;Total\nAlpha & Sons;1 250,75\n".getBytes(StandardCharsets.UTF_8),
                        Set.of(OutputFormat.HTML, OutputFormat.PDF));
        assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.PENDING);
        assertThat(job.getNormalizedPayload().at("/custom/invoice/total").decimalValue())
                .isEqualByComparingTo("1250.75");

        worker.generate(job.getId());
        worker.generate(job.getId());

        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationJobStatus.COMPLETED);
        assertThat(documents.findAllByGenerationJobIdOrderByFormat(job.getId())).hasSize(2);
        var html = generation.output(tenant.getId(), job.getId(), OutputFormat.HTML);
        assertThat(new String(storage.get(html.getStorageKey()), StandardCharsets.UTF_8))
                .contains("Alpha &amp; Sons", "1250.75");
        var pdf = generation.output(tenant.getId(), job.getId(), OutputFormat.PDF);
        assertThat(storage.get(pdf.getStorageKey()))
                .startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
    }

    @TestConfiguration
    static class StorageConfig {
        @Bean
        @Primary
        DocumentStorage inMemoryDocumentStorage() {
            return new InMemoryStorage();
        }
    }

    static class InMemoryStorage implements DocumentStorage {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();

        @Override
        public StoredObject put(String key, byte[] content, String mediaType) {
            values.put(key, content.clone());
            try {
                String sha =
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(content));
                return new StoredObject(key, mediaType, content.length, sha);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public byte[] get(String key) {
            return values.get(key).clone();
        }
    }
}
