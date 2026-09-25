package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.*;
import io.collectra.api.communication.application.*;
import io.collectra.api.communication.domain.*;
import io.collectra.api.communication.infrastructure.*;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.document.application.*;
import io.collectra.api.document.domain.GenerationJobStatus;
import io.collectra.api.document.infrastructure.*;
import io.collectra.api.importing.application.BusinessRecordPersistenceService;
import io.collectra.api.importing.application.MappingExecutionService;
import io.collectra.api.importing.domain.*;
import io.collectra.api.importing.infrastructure.*;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.template.domain.*;
import io.collectra.api.template.infrastructure.*;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

@Import(IntegrationPipelineExecutableSmokeTest.StorageConfig.class)
@SpringBootTest(
        properties = {
            "collectra.communication.delivery.enabled=true",
            "collectra.communication.delivery.provider=simulated",
            "collectra.communication.delivery.simulation.success-rate-percent=100",
            "collectra.communication.delivery.simulation.permanent-failure-rate-percent=0"
        })
class IntegrationPipelineExecutableSmokeTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired TenantLocaleRepository locales;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired SourceSchemaRepository schemas;
    @Autowired SourceFieldRepository sourceFields;
    @Autowired MappingProfileRepository mappings;
    @Autowired MappingRuleRepository rules;
    @Autowired FieldDefinitionRepository fields;
    @Autowired MappingExecutionService mappingExecution;
    @Autowired BusinessRecordPersistenceService persistence;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CampaignService campaigns;
    @Autowired CampaignAttachmentService campaignAttachments;
    @Autowired CampaignMessageMaterializer materializer;
    @Autowired DocumentGenerationWorker documentWorker;
    @Autowired MessageAttachmentService attachmentService;
    @Autowired MessageDeliveryWorker deliveryWorker;
    @Autowired MessageRepository messages;
    @Autowired MessageAttachmentRepository attachments;
    @Autowired MessageDeliveryAttemptRepository attempts;
    @Autowired GenerationJobRepository jobs;
    @Autowired GeneratedDocumentRepository documents;
    @Autowired DocumentStorage storage;
    @Autowired ObjectMapper json;

    @Test
    void fixtureTraversesRealMappingPersistenceRenderingAttachmentAndDeliveryPipeline() {
        Tenant tenant = tenant("i0-success");
        Tenant foreign = tenant("i0-foreign");
        var customer =
                customers.create(
                        tenant.getId(),
                        "ERP-C-I0",
                        CustomerType.COMPANY,
                        "I0 Customer",
                        null,
                        null,
                        null,
                        "I0 LLP",
                        null,
                        "ru",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), customer.getId(), "i0@example.test", "WORK", true);

        MappingFixture mapping = mapping(tenant);
        LocalDate today = LocalDate.now();
        byte[] fixture =
                ("Invoice;Customer;Number;InvoiceDate;DueDate;Amount;Currency\n"
                                + "ERP-I-I0;ERP-C-I0;INV-I0;"
                                + today.minusDays(20)
                                + ";"
                                + today.minusDays(10)
                                + ";125000.00;KZT\n")
                        .getBytes(StandardCharsets.UTF_8);

        var normalized =
                mappingExecution.execute(tenant.getId(), mapping.profile().getId(), fixture);
        assertThat(normalized.normalizedPayload().at("/customer/externalId").asText())
                .isEqualTo("ERP-C-I0");
        assertThat(normalized.normalizedPayload().at("/invoice/externalId").asText())
                .isEqualTo("ERP-I-I0");
        assertThat(normalized.normalizedPayload().at("/invoice/amount").decimalValue())
                .isEqualByComparingTo("125000.00");

        var persisted =
                persistence.persist(
                        tenant.getId(), normalized.documentType(), normalized.normalizedPayload());
        assertThat(persisted.created()).isTrue();
        assertThat(persisted.externalId()).isEqualTo("ERP-I-I0");
        var invoice = receivables.findInvoiceByExternalId(tenant.getId(), "ERP-I-I0").orElseThrow();
        assertThat(invoice.getCustomerId()).isEqualTo(customer.getId());
        assertThat(invoice.getOriginalAmount()).isEqualByComparingTo("125000.0000");
        assertThat(receivables.findInvoiceByExternalId(foreign.getId(), "ERP-I-I0")).isEmpty();
        assertThat(customers.findByExternalId(foreign.getId(), "ERP-C-I0")).isEmpty();

        TemplateVersion email =
                template(
                        tenant,
                        TemplateChannel.EMAIL,
                        "I0_EMAIL",
                        "Debt {{invoice.number}}",
                        "<p>{{customer.name}} owes {{invoice.outstandingAmount}}</p>",
                        "INVOICE");
        TemplateVersion pdf =
                template(
                        tenant,
                        TemplateChannel.PDF,
                        "I0_PDF",
                        null,
                        "<h1>{{invoice.number}}</h1><p>{{customer.name}}</p>",
                        "INVOICE");
        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "I0 executable smoke",
                        email.getId(),
                        "EMAIL",
                        null,
                        CampaignSelection.customer(Set.of(customer.getId()), Set.of()),
                        pdf.getId(),
                        false,
                        null);
        campaignAttachments.configureGeneratedPdf(tenant.getId(), campaign.getId(), true, true);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
        assertThat(prepared.recipients()).isOne();

        var batch = materializer.materializeNextBatch(tenant.getId(), prepared.runId(), 100);
        assertThat(batch.queued()).isOne();
        Message queued =
                messages.findAllByTenantIdAndCampaignRunId(
                                tenant.getId(),
                                prepared.runId(),
                                org.springframework.data.domain.PageRequest.of(0, 10))
                        .getContent()
                        .get(0);
        assertThat(queued.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(queued.getDestination()).isEqualTo("i0@example.test");
        assertThat(
                        messages.findAllByTenantIdAndCampaignRunId(
                                        foreign.getId(),
                                        prepared.runId(),
                                        org.springframework.data.domain.PageRequest.of(0, 10))
                                .getContent())
                .isEmpty();

        var attachment =
                attachments
                        .findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(
                                tenant.getId(), queued.getId())
                        .get(0);
        assertThat(attachment.getStatus()).isEqualTo(MessageAttachmentStatus.PENDING);
        var job = jobs.findById(attachment.getGenerationJobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.PENDING);

        documentWorker.generate(foreign.getId(), job.getId());
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationJobStatus.PENDING);
        documentWorker.generate(tenant.getId(), job.getId());
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationJobStatus.COMPLETED);
        var generated =
                documents
                        .findByTenantIdAndGenerationJobIdAndFormat(
                                tenant.getId(),
                                job.getId(),
                                io.collectra.api.document.domain.OutputFormat.PDF)
                        .orElseThrow();
        assertThat(storage.get(generated.getStorageKey()))
                .startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));

        assertThat(attachmentService.generationCompleted(tenant.getId(), job.getId())).isTrue();
        assertThat(
                        attachments
                                .findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(
                                        tenant.getId(), queued.getId())
                                .get(0)
                                .getStatus())
                .isEqualTo(MessageAttachmentStatus.READY);
        assertThat(messages.findById(queued.getId()).orElseThrow().getDeliveryRequestedAt())
                .isNotNull();

        deliveryWorker.deliver(foreign.getId(), queued.getId());
        assertThat(messages.findById(queued.getId()).orElseThrow().getStatus())
                .isEqualTo(MessageStatus.QUEUED);
        assertThat(attempts.findAllByMessageIdOrderByAttemptNoAsc(queued.getId())).isEmpty();

        deliveryWorker.deliver(tenant.getId(), queued.getId());
        Message sent = messages.findById(queued.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(sent.getProviderMessageId()).isEqualTo("simulated:email:" + queued.getId());
        assertThat(attempts.findAllByMessageIdOrderByAttemptNoAsc(queued.getId()))
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getProviderReference())
                                    .isEqualTo("simulated:email:" + queued.getId());
                            assertThat(a.getErrorCode()).isNull();
                        });
    }

    @Test
    void mappingFailureStopsBeforePersistenceGenerationMessageAndDelivery() {
        Tenant tenant = tenant("i0-failure");
        MappingFixture mapping = mapping(tenant);
        long jobsBefore = jobs.count(),
                messagesBefore = messages.count(),
                attemptsBefore = attempts.count();
        byte[] invalid =
                ("Invoice;Customer;Number;InvoiceDate;DueDate;Amount;Currency\n"
                                + "ERP-I-BAD;MISSING;INV-BAD;2026-09-01;;not-a-number;KZT\n")
                        .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(
                        () ->
                                mappingExecution.execute(
                                        tenant.getId(), mapping.profile().getId(), invalid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(receivables.findInvoiceByExternalId(tenant.getId(), "ERP-I-BAD")).isEmpty();
        assertThat(jobs.count()).isEqualTo(jobsBefore);
        assertThat(messages.count()).isEqualTo(messagesBefore);
        assertThat(attempts.count()).isEqualTo(attemptsBefore);
    }

    private MappingFixture mapping(Tenant tenant) {
        SourceSchema schema =
                new SourceSchema(
                        tenant.getId(),
                        "I0_CSV_" + UUID.randomUUID(),
                        "I0 CSV",
                        SourceFormat.CSV,
                        1);
        schemas.saveAndFlush(schema);
        var invoice = field(schema, "Invoice", "STRING", 1, true);
        var customer = field(schema, "Customer", "STRING", 2, false);
        var number = field(schema, "Number", "STRING", 3, false);
        var invoiceDate = field(schema, "InvoiceDate", "DATE", 4, false);
        var dueDate = field(schema, "DueDate", "DATE", 5, false);
        var amount = field(schema, "Amount", "DECIMAL", 6, false);
        var currency = field(schema, "Currency", "STRING", 7, false);
        schema.validated();
        schema.publish();
        schemas.saveAndFlush(schema);
        MappingProfile profile =
                new MappingProfile(
                        tenant.getId(),
                        schema.getId(),
                        "I0_MAP_" + UUID.randomUUID(),
                        "I0 mapping",
                        "INVOICE",
                        1);
        profile.validated();
        profile.publish();
        mappings.saveAndFlush(profile);
        rule(profile, invoice, "invoice.externalId", "TRIM");
        rule(profile, customer, "customer.externalId", "TRIM");
        rule(profile, number, "invoice.invoiceNumber", "TRIM");
        rule(profile, invoiceDate, "invoice.invoiceDate", "DATE_PARSE");
        rule(profile, dueDate, "invoice.dueDate", "DATE_PARSE");
        rule(profile, amount, "invoice.amount", "DECIMAL_PARSE");
        rule(profile, currency, "invoice.currency", "TRIM");
        return new MappingFixture(schema, profile);
    }

    private SourceField field(SourceSchema s, String path, String type, int position, boolean key) {
        return sourceFields.saveAndFlush(
                new SourceField(
                        s.getId(),
                        path,
                        type,
                        null,
                        true,
                        position,
                        SourceFieldScope.DOCUMENT,
                        key,
                        FieldValuePolicy.REQUIRE_SAME));
    }

    private void rule(MappingProfile p, SourceField source, String targetKey, String transform) {
        FieldDefinition target = fields.findById(targetFieldId(targetKey)).orElseThrow();
        var cfg = json.createObjectNode().put("type", transform);
        if (transform.equals("DATE_PARSE")) cfg.put("pattern", "yyyy-MM-dd");
        if (transform.equals("DECIMAL_PARSE")) cfg.put("decimalSeparator", ".");
        rules.saveAndFlush(
                new MappingRule(p.getId(), source.getId(), target.getId(), cfg, null, true));
    }

    private UUID targetFieldId(String key) {
        return UUID.fromString(
                switch (key) {
                    case "customer.externalId" -> "20000000-0000-0000-0000-000000000010";
                    case "invoice.externalId" -> "20000000-0000-0000-0000-000000000020";
                    case "invoice.invoiceNumber" -> "20000000-0000-0000-0000-000000000021";
                    case "invoice.invoiceDate" -> "20000000-0000-0000-0000-000000000022";
                    case "invoice.dueDate" -> "20000000-0000-0000-0000-000000000023";
                    case "invoice.amount" -> "20000000-0000-0000-0000-000000000024";
                    case "invoice.currency" -> "20000000-0000-0000-0000-000000000025";
                    default ->
                            throw new IllegalArgumentException("Unknown I0 target field: " + key);
                });
    }

    private Tenant tenant(String prefix) {
        Tenant t = tenants.saveAndFlush(new Tenant(prefix + "-" + UUID.randomUUID(), "I0 Tenant"));
        locales.saveAndFlush(new TenantLocale(t.getId(), "ru", true, true, 0));
        return t;
    }

    private TemplateVersion template(
            Tenant tenant,
            TemplateChannel channel,
            String code,
            String subject,
            String body,
            String type) {
        DocumentTemplate t =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(), code + "_" + UUID.randomUUID(), code, type));
        TemplateVersion v = new TemplateVersion(t.getId(), 1, "ru", channel, subject, body, null);
        v.validated();
        v.publish();
        return versions.saveAndFlush(v);
    }

    private record MappingFixture(SourceSchema schema, MappingProfile profile) {}

    @TestConfiguration
    static class StorageConfig {
        @Bean
        @Primary
        DocumentStorage storage() {
            return new InMemoryStorage();
        }
    }

    static class InMemoryStorage implements DocumentStorage {
        private final Map<String, byte[]> data = new ConcurrentHashMap<>();

        public StoredObject put(String key, byte[] content, String mediaType) {
            data.put(key, content.clone());
            try {
                return new StoredObject(
                        key,
                        mediaType,
                        content.length,
                        java.util.HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public byte[] get(String key) {
            byte[] value = data.get(key);
            if (value == null) throw new NoSuchElementException(key);
            return value.clone();
        }
    }
}
