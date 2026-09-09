package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.document.application.DocumentStorage;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import io.collectra.api.importing.application.ImportBatchService;
import io.collectra.api.importing.application.ImportBatchFailedException;
import io.collectra.api.importing.domain.*;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.MappingRuleRepository;
import io.collectra.api.importing.infrastructure.SourceFieldRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(TabularImportBatchIntegrationTest.StorageConfig.class)
class TabularImportBatchIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired SourceSchemaRepository schemas;
    @Autowired SourceFieldRepository sourceFields;
    @Autowired MappingProfileRepository profiles;
    @Autowired MappingRuleRepository rules;
    @Autowired FieldDefinitionRepository fields;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired GenerationJobRepository jobs;
    @Autowired ImportBatchService batches;
    @Autowired ObjectMapper json;

    @Test
    void createsOrderedJobsAndItemsAndReplaysIdempotentRequest() throws Exception {
        Tenant tenant = tenants.saveAndFlush(new Tenant("batch-" + UUID.randomUUID(), "Batch"));
        FieldDefinition documentNumber = field("document.number");
        FieldDefinition itemName = field("items.name");
        FieldDefinition itemQuantity = field("items.quantity");

        SourceSchema schema = new SourceSchema(tenant.getId(), "BATCH_CSV", "Batch CSV",
                SourceFormat.CSV, 1);
        schemas.saveAndFlush(schema);
        SourceField type = sourceFields.saveAndFlush(new SourceField(schema.getId(), "Type",
                "STRING", "ITEM", true, 1, SourceFieldScope.ROW_CONTROL, false,
                FieldValuePolicy.FIRST_NON_EMPTY));
        SourceField invoice = sourceFields.saveAndFlush(new SourceField(schema.getId(), "Invoice",
                "STRING", "INV-1", true, 2, SourceFieldScope.DOCUMENT, true,
                FieldValuePolicy.REQUIRE_SAME));
        SourceField item = sourceFields.saveAndFlush(new SourceField(schema.getId(), "Item",
                "STRING", "Paper", false, 3, SourceFieldScope.ITEM, false,
                FieldValuePolicy.FIRST_NON_EMPTY));
        SourceField quantity = sourceFields.saveAndFlush(new SourceField(schema.getId(), "Quantity",
                "DECIMAL", "2", false, 4, SourceFieldScope.ITEM, false,
                FieldValuePolicy.FIRST_NON_EMPTY));
        schema.configureRows(null, type.getId(), Set.of("ITEM"), Set.of("TOTAL"), Set.of("IGNORE"));
        schema.validated(); schema.publish(); schemas.saveAndFlush(schema);

        MappingProfile profile = new MappingProfile(tenant.getId(), schema.getId(), "BATCH_MAP",
                "Batch map", "INVOICE", 1);
        profile.validated(); profile.publish(); profiles.saveAndFlush(profile);
        rules.saveAndFlush(new MappingRule(profile.getId(), invoice.getId(), documentNumber.getId(),
                json.createObjectNode().put("type", "TRIM"), null, true));
        rules.saveAndFlush(new MappingRule(profile.getId(), item.getId(), itemName.getId(),
                json.createObjectNode().put("type", "TRIM"), null, false));
        rules.saveAndFlush(new MappingRule(profile.getId(), quantity.getId(), itemQuantity.getId(),
                json.createObjectNode().put("type", "DECIMAL_PARSE").put("decimalSeparator", "."),
                null, false));

        DocumentTemplate template = templates.saveAndFlush(new DocumentTemplate(tenant.getId(),
                "BATCH_INVOICE", "Batch invoice", "INVOICE"));
        TemplateVersion templateVersion = new TemplateVersion(template.getId(), 1, "en",
                "<p>{{document.number}}</p>", null);
        templateVersion.validated(); templateVersion.publish(); versions.saveAndFlush(templateVersion);

        byte[] input = ("Type;Invoice;Item;Quantity\n"
                + "ITEM;INV-1;Paper;2\nITEM;INV-1;Pen;5\nTOTAL;INV-1;;\n"
                + "ITEM;INV-2;Printer;1\n").getBytes(StandardCharsets.UTF_8);
        var created = batches.create(tenant.getId(), "erp-event-1", profile.getId(),
                templateVersion.getId(), input, Set.of(OutputFormat.HTML));
        assertThat(created.documentCount()).isEqualTo(2);
        assertThat(created.documents()).extracting(ImportBatchService.DocumentResult::documentKey)
                .containsExactly("INV-1", "INV-2");
        var firstJob = jobs.findById(created.documents().get(0).generationJobId()).orElseThrow();
        assertThat(firstJob.getNormalizedPayload().at("/items")).hasSize(2);
        assertThat(firstJob.getNormalizedPayload().at("/items/0/name").asText()).isEqualTo("Paper");
        assertThat(firstJob.getNormalizedPayload().at("/items/1/name").asText()).isEqualTo("Pen");

        var replay = batches.create(tenant.getId(), "erp-event-1", profile.getId(),
                templateVersion.getId(), input, Set.of(OutputFormat.HTML));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.batchId()).isEqualTo(created.batchId());
        assertThatThrownBy(() -> batches.create(tenant.getId(), "erp-event-1", profile.getId(),
                templateVersion.getId(), "other".getBytes(StandardCharsets.UTF_8),
                Set.of(OutputFormat.HTML))).isInstanceOf(IllegalArgumentException.class);

        long jobCount = jobs.count();
        byte[] invalid = ("Type;Invoice;Item;Quantity\nITEM;;Paper;2\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportBatchFailedException failed = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> batches.create(tenant.getId(), "erp-event-failed", profile.getId(),
                        templateVersion.getId(), invalid, Set.of(OutputFormat.HTML)),
                ImportBatchFailedException.class);
        assertThat(failed.getErrorCode()).isEqualTo("INVALID_IMPORT_INPUT");
        var failedBatch = batches.get(tenant.getId(), failed.getBatchId());
        assertThat(failedBatch.status()).isEqualTo("FAILED");
        assertThat(failedBatch.failure().code()).isEqualTo("INVALID_IMPORT_INPUT");
        assertThat(jobs.count()).isEqualTo(jobCount);

        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<ImportBatchService.BatchResult> reserve = () -> {
                ready.countDown();
                start.await();
                return batches.create(tenant.getId(), "concurrent-key", profile.getId(),
                        templateVersion.getId(), input, Set.of(OutputFormat.HTML));
            };
            var firstFuture = executor.submit(reserve);
            var secondFuture = executor.submit(reserve);
            ready.await();
            start.countDown();
            var first = firstFuture.get();
            var second = secondFuture.get();
            assertThat(first.batchId()).isEqualTo(second.batchId());
            assertThat(java.util.List.of(first, second)).filteredOn(
                    ImportBatchService.BatchResult::replayed).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private FieldDefinition field(String key) {
        return fields.findAvailable(null).stream().filter(value -> value.getKey().equals(key))
                .findFirst().orElseThrow();
    }

    @TestConfiguration
    static class StorageConfig {
        @Bean @Primary DocumentStorage storage() {
            return new DocumentStorage() {
                public StoredObject put(String key, byte[] content, String mediaType) {
                    return new StoredObject(key, mediaType, content.length, "0".repeat(64));
                }
                public byte[] get(String key) { return new byte[0]; }
            };
        }
    }
}
