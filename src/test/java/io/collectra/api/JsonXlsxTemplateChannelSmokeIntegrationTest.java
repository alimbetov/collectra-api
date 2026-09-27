package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignAttachmentService;
import io.collectra.api.campaign.application.CampaignMessageMaterializer;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.communication.application.MessageAttachmentService;
import io.collectra.api.communication.application.MessageDeliveryRequested;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.communication.infrastructure.MessageDeliveryAttemptRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.document.application.DocumentGenerationWorker;
import io.collectra.api.document.application.DocumentStorage;
import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.GenerationJobStatus;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import io.collectra.api.document.infrastructure.GenerationJobRepository;
import io.collectra.api.importing.application.ImportBatchService;
import io.collectra.api.importing.domain.FieldValuePolicy;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.MappingRule;
import io.collectra.api.importing.domain.SourceField;
import io.collectra.api.importing.domain.SourceFieldScope;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.MappingRuleRepository;
import io.collectra.api.importing.infrastructure.SourceFieldRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.integration.application.ServiceClientService;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.shared.outbox.OutboxPublisher;
import io.collectra.api.shared.outbox.OutboxRepository;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;

@Import(JsonXlsxTemplateChannelSmokeIntegrationTest.StorageConfig.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        properties = {
            "collectra.messaging.outbox-enabled=true",
            "collectra.communication.delivery.enabled=true",
            "collectra.communication.delivery.provider=simulated",
            "collectra.communication.delivery.simulation.success-rate-percent=100",
            "collectra.communication.delivery.simulation.permanent-failure-rate-percent=0",
            "spring.rabbitmq.listener.simple.auto-startup=true"
        })
class JsonXlsxTemplateChannelSmokeIntegrationTest extends AbstractIntegrationTest {
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    private static final String FIXTURE_ROOT = "smoke/json-xlsx-template-channel/";
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(60);

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired TenantRepository tenants;
    @Autowired TenantLocaleRepository locales;
    @Autowired ServiceClientService serviceClients;
    @Autowired SourceSchemaRepository schemas;
    @Autowired SourceFieldRepository sourceFields;
    @Autowired MappingProfileRepository profiles;
    @Autowired MappingRuleRepository rules;
    @Autowired FieldDefinitionRepository fields;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired GenerationJobRepository jobs;
    @Autowired GeneratedDocumentRepository documents;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CampaignService campaigns;
    @Autowired CampaignAttachmentService campaignAttachments;
    @Autowired CampaignMessageMaterializer materializer;
    @Autowired MessageAttachmentService attachmentService;
    @Autowired MessageRepository messages;
    @Autowired MessageAttachmentRepository attachments;
    @Autowired MessageDeliveryAttemptRepository attempts;
    @Autowired OutboxRepository outboxEvents;
    @Autowired OutboxPublisher outbox;
    @Autowired DocumentGenerationWorker documentGenerationWorker;
    @Autowired DocumentStorage storage;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mockMvc;

    @Test
    void jsonAndRealXlsxImportsRenderTemplateGeneratePdfAndReachChannelDelivery() throws Exception {
        Tenant tenant = tenant("ps01-alpha");
        Tenant foreign = tenant("ps01-beta");
        String accessToken =
                serviceToken(tenant.getId(), Set.of("document:generate", "document:read"));
        String foreignToken =
                serviceToken(foreign.getId(), Set.of("document:generate", "document:read"));

        TemplateVersion importTemplate =
                publishedTemplate(
                        tenant,
                        TemplateChannel.PDF,
                        "PS01_IMPORT",
                        null,
                        fixtureText("template.html"),
                        "INVOICE");
        MappingFixture jsonMapping = mapping(tenant, SourceFormat.JSON, jsonPaths());
        MappingFixture xlsxMapping = mapping(tenant, SourceFormat.EXCEL, xlsxColumns());

        JsonNode expected = json.readTree(fixtureText("expected-canonical.json"));
        byte[] jsonBody = fixtureBytes("input.json");
        byte[] xlsxBody = workbookFromCsv(fixtureText("input-xlsx.csv"));

        ImportBatchService.BatchResult jsonBatch =
                postJsonImport(
                        accessToken,
                        jsonMapping.profile().getId(),
                        importTemplate.getId(),
                        "ps01-json-" + UUID.randomUUID(),
                        jsonBody,
                        status().isAccepted());
        ImportBatchService.BatchResult xlsxBatch =
                postXlsxImport(
                        accessToken,
                        xlsxMapping.profile().getId(),
                        importTemplate.getId(),
                        "ps01-xlsx-" + UUID.randomUUID(),
                        xlsxBody,
                        status().isAccepted());

        assertThat(jsonBatch.documentCount()).isOne();
        assertThat(xlsxBatch.documentCount()).isOne();
        JsonNode jsonPayload = normalizedPayload(jsonBatch);
        JsonNode xlsxPayload = normalizedPayload(xlsxBatch);
        assertCanonical(jsonPayload, expected);
        assertThat(xlsxPayload).isEqualTo(jsonPayload);

        GeneratedPair jsonOutputs = awaitGeneratedOutputs(tenant.getId(), jsonBatch);
        GeneratedPair xlsxOutputs = awaitGeneratedOutputs(tenant.getId(), xlsxBatch);
        assertHtml(jsonOutputs.html());
        assertHtml(xlsxOutputs.html());
        assertPdf(jsonOutputs.pdf());
        assertPdf(xlsxOutputs.pdf());

        String replayKey = "ps01-replay-" + UUID.randomUUID();
        ImportBatchService.BatchResult firstReplay =
                postJsonImport(
                        accessToken,
                        jsonMapping.profile().getId(),
                        importTemplate.getId(),
                        replayKey,
                        jsonBody,
                        status().isAccepted());
        ImportBatchService.BatchResult secondReplay =
                postJsonImport(
                        accessToken,
                        jsonMapping.profile().getId(),
                        importTemplate.getId(),
                        replayKey,
                        jsonBody,
                        status().isAccepted());
        assertThat(secondReplay.replayed()).isTrue();
        assertThat(secondReplay.batchId()).isEqualTo(firstReplay.batchId());
        postJsonImport(
                accessToken,
                jsonMapping.profile().getId(),
                importTemplate.getId(),
                replayKey,
                changedJsonBody(),
                status().is4xxClientError());

        postJsonImport(
                foreignToken,
                jsonMapping.profile().getId(),
                importTemplate.getId(),
                "ps01-foreign-profile-" + UUID.randomUUID(),
                jsonBody,
                status().is4xxClientError());

        var customer =
                customers.create(
                        tenant.getId(),
                        expected.at("/customer/externalId").asText(),
                        CustomerType.COMPANY,
                        expected.at("/customer/displayName").asText(),
                        null,
                        null,
                        null,
                        expected.at("/customer/displayName").asText(),
                        null,
                        "ru",
                        expected.at("/customer/timezone").asText(),
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), customer.getId(), "ps01@example.test", "WORK", true);
        var invoice =
                receivables.createInvoice(
                        tenant.getId(),
                        customer.getId(),
                        null,
                        expected.at("/invoice/externalId").asText(),
                        expected.at("/invoice/invoiceNumber").asText(),
                        LocalDate.now().minusDays(20),
                        LocalDate.now().minusDays(10),
                        new BigDecimal(expected.at("/invoice/amount").asText()),
                        expected.at("/invoice/currency").asText(),
                        null,
                        json.createObjectNode());

        TemplateVersion emailTemplate =
                publishedTemplate(
                        tenant,
                        TemplateChannel.EMAIL,
                        "PS01_EMAIL",
                        "Invoice {{invoice.invoiceNumber}}",
                        "<p>Hello {{customer.name}}</p><p>{{invoice.invoiceNumber}}</p>",
                        "INVOICE");
        TemplateVersion pdfTemplate =
                publishedTemplate(
                        tenant,
                        TemplateChannel.PDF,
                        "PS01_CHANNEL_PDF",
                        null,
                        fixtureText("template.html"),
                        "INVOICE");
        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "PS-01 channel smoke",
                        emailTemplate.getId(),
                        CommunicationChannel.EMAIL.name(),
                        null,
                        new CampaignSelection(
                                Set.of(customer.getId()), Set.of(), 1, 60, null, null),
                        pdfTemplate.getId(),
                        false,
                        null);
        campaignAttachments.configureGeneratedPdf(tenant.getId(), campaign.getId(), true, true);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());

        assertThat(
                        materializer
                                .materializeNextBatch(tenant.getId(), prepared.runId(), 100)
                                .queued())
                .isOne();
        Message queued = onlyMessage(tenant.getId(), prepared.runId());
        assertThat(queued.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(queued.getDeliveryRequestedAt()).isNull();
        assertThat(queued.getSubject()).isEqualTo("Invoice INV-2026-SMOKE-001");
        assertThat(queued.getBody())
                .contains("ТОО &laquo;Алматы Тест&raquo;")
                .contains("INV-2026-SMOKE-001");

        var attachment =
                attachments
                        .findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(
                                tenant.getId(), queued.getId())
                        .get(0);
        assertThat(attachment.getStatus()).isEqualTo(MessageAttachmentStatus.PENDING);
        assertThat(deliveryEvents(tenant.getId(), queued.getId())).isZero();

        awaitGenerationCompleted(tenant.getId(), attachment.getGenerationJobId());
        await().atMost(ASYNC_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(
                        () ->
                                assertThat(
                                                attachments
                                                        .findByTenantIdAndGenerationJobId(
                                                                tenant.getId(),
                                                                attachment.getGenerationJobId())
                                                        .orElseThrow()
                                                        .getStatus())
                                        .isEqualTo(MessageAttachmentStatus.READY));
        assertThat(messages.findById(queued.getId()).orElseThrow().getDeliveryRequestedAt())
                .isNotNull();
        assertThat(deliveryEvents(tenant.getId(), queued.getId())).isOne();
        assertThat(
                        attachmentService.generationCompleted(
                                tenant.getId(), attachment.getGenerationJobId()))
                .isFalse();
        assertThat(deliveryEvents(tenant.getId(), queued.getId())).isOne();

        outbox.publishPending();
        await().atMost(ASYNC_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(
                        () -> {
                            outbox.publishPending();
                            Message sent = messages.findById(queued.getId()).orElseThrow();
                            assertThat(sent.getStatus()).isEqualTo(MessageStatus.SENT);
                            assertThat(sent.getProviderMessageId())
                                    .isEqualTo("simulated:email:" + queued.getId());
                        });
        assertThat(attempts.findAllByMessageIdOrderByAttemptNoAsc(queued.getId()))
                .singleElement()
                .satisfies(
                        attempt ->
                                assertThat(attempt.getProviderReference())
                                        .isEqualTo("simulated:email:" + queued.getId()));
    }

    private ImportBatchService.BatchResult postJsonImport(
            String token,
            UUID mappingId,
            UUID templateId,
            String idempotencyKey,
            byte[] body,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus)
            throws Exception {
        var result =
                mockMvc.perform(
                                post("/api/v1/import-batches/json")
                                        .header("Authorization", "Bearer " + token)
                                        .header("Idempotency-Key", idempotencyKey)
                                        .param("mappingProfileVersionId", mappingId.toString())
                                        .param("templateVersionId", templateId.toString())
                                        .param("formats", "HTML,PDF")
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(expectedStatus)
                        .andReturn();
        if (result.getResponse().getStatus() >= 400) {
            return null;
        }
        return json.readValue(
                result.getResponse().getContentAsByteArray(), ImportBatchService.BatchResult.class);
    }

    private ImportBatchService.BatchResult postXlsxImport(
            String token,
            UUID mappingId,
            UUID templateId,
            String idempotencyKey,
            byte[] body,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus)
            throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "input.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        body);
        var result =
                mockMvc.perform(
                                multipart("/api/v1/import-batches")
                                        .file(file)
                                        .header("Authorization", "Bearer " + token)
                                        .header("Idempotency-Key", idempotencyKey)
                                        .param("mappingProfileVersionId", mappingId.toString())
                                        .param("templateVersionId", templateId.toString())
                                        .param("formats", "HTML,PDF"))
                        .andExpect(expectedStatus)
                        .andReturn();
        return json.readValue(
                result.getResponse().getContentAsByteArray(), ImportBatchService.BatchResult.class);
    }

    private GeneratedPair awaitGeneratedOutputs(
            UUID tenantId, ImportBatchService.BatchResult batch) {
        UUID jobId = batch.documents().get(0).generationJobId();
        awaitGenerationCompleted(tenantId, jobId);
        GeneratedDocument html =
                documents
                        .findByTenantIdAndGenerationJobIdAndFormat(
                                tenantId, jobId, OutputFormat.HTML)
                        .orElseThrow();
        GeneratedDocument pdf =
                documents
                        .findByTenantIdAndGenerationJobIdAndFormat(
                                tenantId, jobId, OutputFormat.PDF)
                        .orElseThrow();
        return new GeneratedPair(
                new String(storage.get(html.getStorageKey()), StandardCharsets.UTF_8),
                storage.get(pdf.getStorageKey()));
    }

    private void awaitGenerationCompleted(UUID tenantId, UUID jobId) {
        await().atMost(ASYNC_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(
                        () -> {
                            outbox.publishPending();
                            var status =
                                    jobs.findByIdAndTenantId(jobId, tenantId)
                                            .orElseThrow()
                                            .getStatus();
                            if (status == GenerationJobStatus.PENDING) {
                                documentGenerationWorker.generate(tenantId, jobId);
                                outbox.publishPending();
                            }
                            assertThat(
                                            jobs.findByIdAndTenantId(jobId, tenantId)
                                                    .orElseThrow()
                                                    .getStatus())
                                    .isEqualTo(GenerationJobStatus.COMPLETED);
                        });
    }

    private JsonNode normalizedPayload(ImportBatchService.BatchResult batch) {
        return jobs.findById(batch.documents().get(0).generationJobId())
                .orElseThrow()
                .getNormalizedPayload();
    }

    private void assertCanonical(JsonNode actual, JsonNode expected) {
        assertThat(actual.at("/customer/externalId"))
                .isEqualTo(expected.at("/customer/externalId"));
        assertThat(actual.at("/customer/displayName"))
                .isEqualTo(expected.at("/customer/displayName"));
        assertThat(actual.at("/customer/locale")).isEqualTo(expected.at("/customer/locale"));
        assertThat(actual.at("/customer/timezone")).isEqualTo(expected.at("/customer/timezone"));
        assertThat(actual.at("/invoice/externalId")).isEqualTo(expected.at("/invoice/externalId"));
        assertThat(actual.at("/invoice/invoiceNumber"))
                .isEqualTo(expected.at("/invoice/invoiceNumber"));
        assertThat(actual.at("/invoice/invoiceDate"))
                .isEqualTo(expected.at("/invoice/invoiceDate"));
        assertThat(actual.at("/invoice/dueDate")).isEqualTo(expected.at("/invoice/dueDate"));
        assertThat(actual.at("/invoice/amount").decimalValue())
                .isEqualByComparingTo(expected.at("/invoice/amount").decimalValue());
        assertThat(actual.at("/invoice/currency")).isEqualTo(expected.at("/invoice/currency"));
        assertThat(actual.at("/payment/reference")).isEqualTo(expected.at("/payment/reference"));
    }

    private void assertHtml(String html) {
        assertThat(html)
                .contains("INV-2026-SMOKE-001")
                .contains("ТОО &laquo;Алматы Тест&raquo;")
                .contains("125000.5")
                .contains("KZT")
                .contains("PAY-SMOKE-001")
                .doesNotContain("{{");
    }

    private void assertPdf(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        try (PDDocument document = PDDocument.load(pdf)) {
            String extracted = new PDFTextStripper().getText(document).replace('\u00A0', ' ');
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(extracted)
                    .contains("INV-2026-SMOKE-001")
                    .contains("ТОО «Алматы Тест»")
                    .contains("125000.5")
                    .contains("PAY-SMOKE-001");
            assertThat(extracted).doesNotContain("{{");
        }
    }

    private MappingFixture mapping(Tenant tenant, SourceFormat format, List<String> sourcePaths) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        SourceSchema schema =
                schemas.saveAndFlush(
                        new SourceSchema(
                                tenant.getId(),
                                "PS01_" + format + "_" + suffix,
                                "PS01 " + format,
                                format,
                                1));
        List<SourceField> source =
                sourcePaths.stream()
                        .map(
                                path ->
                                        sourceFields.saveAndFlush(
                                                new SourceField(
                                                        schema.getId(),
                                                        path,
                                                        path.contains("Amount")
                                                                        || path.endsWith("amount")
                                                                ? "DECIMAL"
                                                                : "STRING",
                                                        null,
                                                        true,
                                                        sourcePaths.indexOf(path) + 1,
                                                        SourceFieldScope.DOCUMENT,
                                                        path.equals(sourcePaths.get(0)),
                                                        FieldValuePolicy.REQUIRE_SAME)))
                        .toList();
        schema.validated();
        schema.publish();
        schemas.saveAndFlush(schema);

        MappingProfile profile =
                profiles.saveAndFlush(
                        new MappingProfile(
                                tenant.getId(),
                                schema.getId(),
                                "PS01_MAP_" + suffix,
                                "PS01 mapping",
                                "INVOICE",
                                1));
        profile.validated();
        profile.publish();
        profiles.saveAndFlush(profile);

        List<String> targets =
                List.of(
                        "customer.externalId",
                        "customer.displayName",
                        "customer.locale",
                        "customer.timezone",
                        "invoice.externalId",
                        "invoice.invoiceNumber",
                        "invoice.invoiceDate",
                        "invoice.dueDate",
                        "invoice.amount",
                        "invoice.currency",
                        "payment.reference");
        for (int i = 0; i < targets.size(); i++) {
            String transform =
                    switch (targets.get(i)) {
                        case "invoice.invoiceDate", "invoice.dueDate" -> "DATE_PARSE";
                        case "invoice.amount" -> "DECIMAL_PARSE";
                        default -> "TRIM";
                    };
            var config = json.createObjectNode().put("type", transform);
            if ("DATE_PARSE".equals(transform)) {
                config.put("pattern", "yyyy-MM-dd");
            }
            if ("DECIMAL_PARSE".equals(transform)) {
                config.put("decimalSeparator", ".");
            }
            rules.saveAndFlush(
                    new MappingRule(
                            profile.getId(),
                            source.get(i).getId(),
                            target(targets.get(i)).getId(),
                            config,
                            null,
                            true));
        }
        return new MappingFixture(profile);
    }

    private FieldDefinition target(String key) {
        return fields.findAvailable(null).stream()
                .filter(value -> value.getKey().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing field " + key));
    }

    private static List<String> jsonPaths() {
        return List.of(
                "customer.externalId",
                "customer.displayName",
                "customer.locale",
                "customer.timezone",
                "invoice.externalId",
                "invoice.invoiceNumber",
                "invoice.invoiceDate",
                "invoice.dueDate",
                "invoice.amount",
                "invoice.currency",
                "payment.reference");
    }

    private static List<String> xlsxColumns() {
        return List.of(
                "Customer External ID",
                "Customer Name",
                "Locale",
                "Timezone",
                "Invoice External ID",
                "Invoice Number",
                "Invoice Date",
                "Due Date",
                "Amount",
                "Currency",
                "Payment Reference");
    }

    private byte[] workbookFromCsv(String csv) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("input");
            String[] lines = csv.strip().split("\\R");
            for (int rowIndex = 0; rowIndex < lines.length; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                String[] cells = lines[rowIndex].split(",", -1);
                for (int col = 0; col < cells.length; col++) {
                    row.createCell(col).setCellValue(unquoteCsvCell(cells[col]));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static String unquoteCsvCell(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }

    private String serviceToken(UUID tenantId, Set<String> scopes) {
        String clientId = "ps01-" + UUID.randomUUID();
        var credential =
                serviceClients.create(tenantId, clientId, "PS01 client", scopes, null, null);
        return serviceClients.token(clientId, credential.clientSecret(), scopes).accessToken();
    }

    private Tenant tenant(String prefix) {
        Tenant tenant =
                tenants.saveAndFlush(new Tenant(prefix + "-" + UUID.randomUUID(), "PS01 Tenant"));
        locales.saveAndFlush(new TenantLocale(tenant.getId(), "ru", true, true, 0));
        return tenant;
    }

    private TemplateVersion publishedTemplate(
            Tenant tenant,
            TemplateChannel channel,
            String code,
            String subject,
            String content,
            String documentType) {
        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                code + "_" + UUID.randomUUID(),
                                code,
                                documentType));
        TemplateVersion version =
                new TemplateVersion(template.getId(), 1, "ru", channel, subject, content, null);
        version.validated();
        version.publish();
        return versions.saveAndFlush(version);
    }

    private Message onlyMessage(UUID tenantId, UUID runId) {
        return messages.findAllByTenantIdAndCampaignRunId(tenantId, runId, PageRequest.of(0, 10))
                .getContent()
                .get(0);
    }

    private long deliveryEvents(UUID tenantId, UUID messageId) {
        return outboxEvents.findAll().stream()
                .filter(event -> tenantId.equals(event.getTenantId()))
                .filter(event -> MessageDeliveryRequested.EVENT_TYPE.equals(event.getEventType()))
                .filter(event -> messageId.equals(event.getAggregateId()))
                .count();
    }

    private byte[] changedJsonBody() {
        return new String(fixtureBytes("input.json"), StandardCharsets.UTF_8)
                .replace("125000.50", "125001.50")
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] fixtureBytes(String name) {
        try {
            return getClass()
                    .getClassLoader()
                    .getResourceAsStream(FIXTURE_ROOT + name)
                    .readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("Missing fixture " + name, ex);
        }
    }

    private String fixtureText(String name) {
        return new String(fixtureBytes(name), StandardCharsets.UTF_8);
    }

    private record MappingFixture(MappingProfile profile) {}

    private record GeneratedPair(String html, byte[] pdf) {}

    @TestConfiguration
    static class StorageConfig {
        @Bean
        @Primary
        DocumentStorage documentStorage() {
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
            byte[] value = values.get(key);
            if (value == null) {
                throw new NoSuchElementException(key);
            }
            return value.clone();
        }
    }
}
