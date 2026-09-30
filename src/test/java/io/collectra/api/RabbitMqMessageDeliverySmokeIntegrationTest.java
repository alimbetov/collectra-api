package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignMessageMaterializer;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.infrastructure.CampaignRecipientRepository;
import io.collectra.api.collection.application.CollectionService;
import io.collectra.api.collection.domain.CollectionPriority;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.importing.domain.*;
import io.collectra.api.importing.infrastructure.*;
import io.collectra.api.integration.application.IngestionApplicationService;
import io.collectra.api.integration.application.IngestionWorker;
import io.collectra.api.integration.application.IntegrationSourceService;
import io.collectra.api.integration.application.ServiceClientService;
import io.collectra.api.integration.domain.IngestionStatus;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService;
import io.collectra.api.reporting.application.TenantFinancialAnalyticsQueryService;
import io.collectra.api.shared.outbox.OutboxPublisher;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;

@Import(ProductionIngestionAcceptanceTest.StorageConfig.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "collectra.messaging.outbox-enabled=true",
            "collectra.communication.delivery.enabled=true",
            "collectra.communication.delivery.provider=simulated",
            "collectra.communication.delivery.simulation.success-rate-percent=100",
            "collectra.communication.delivery.simulation.permanent-failure-rate-percent=0",
            "spring.rabbitmq.listener.simple.auto-startup=true"
        })
class RabbitMqMessageDeliverySmokeIntegrationTest extends AbstractIntegrationTest {
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(60);

    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3.13-management-alpine");

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
    @Autowired IntegrationSourceService integrationSources;
    @Autowired IngestionApplicationService ingestion;
    @Autowired IngestionWorker ingestionWorker;
    @Autowired SourceSchemaDefinitionRepository schemaDefinitions;
    @Autowired SourceSchemaRepository schemas;
    @Autowired SourceFieldRepository sourceFields;
    @Autowired MappingProfileDefinitionRepository mappingDefinitions;
    @Autowired MappingProfileRepository mappings;
    @Autowired MappingRuleRepository mappingRules;
    @Autowired FieldDefinitionRepository fieldDefinitions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CollectionService collections;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CampaignService campaigns;
    @Autowired CampaignMessageMaterializer materializer;
    @Autowired CampaignRecipientRepository campaignRecipients;
    @Autowired MessageRepository messages;
    @Autowired OutboxPublisher outbox;
    @Autowired TenantFinancialAnalyticsQueryService financialAnalytics;
    @Autowired CommunicationAnalyticsQueryService communicationAnalytics;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void outboxTraversesRealRabbitTopologyIntoDeliveryWorker() throws Exception {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("rabbit-smoke-" + UUID.randomUUID(), "Rabbit Smoke"));
        locales.saveAndFlush(new TenantLocale(tenant.getId(), "ru", true, true, 0));
        String serviceClientId = "rabbit-smoke-" + UUID.randomUUID();
        var credential =
                serviceClients.create(
                        tenant.getId(),
                        serviceClientId,
                        "Rabbit smoke integration client",
                        Set.of("integration:imports:create", "integration:imports:read"),
                        null,
                        null);
        var serviceToken =
                serviceClients.token(
                        serviceClientId,
                        credential.clientSecret(),
                        Set.of("integration:imports:create", "integration:imports:read"));
        assertThat(serviceToken.accessToken()).isNotBlank();

        UUID clientId = credential.client().id();
        String customerExternalId = "RABBIT-C-" + UUID.randomUUID();
        String invoiceExternalId = "RABBIT-I-" + UUID.randomUUID();
        String paymentExternalId = "RABBIT-P-" + UUID.randomUUID();
        LocalDate businessDate = LocalDate.now();

        SourceFixture customerSource =
                source(
                        tenant.getId(),
                        clientId,
                        "CUSTOMER",
                        new String[] {"Customer", "DisplayName"},
                        new String[] {"customer.externalId", "customer.displayName"},
                        new String[] {"TRIM", "TRIM"});
        SourceFixture invoiceSource =
                source(
                        tenant.getId(),
                        clientId,
                        "INVOICE",
                        new String[] {
                            "Invoice",
                            "Customer",
                            "Number",
                            "InvoiceDate",
                            "DueDate",
                            "Amount",
                            "Currency"
                        },
                        new String[] {
                            "invoice.externalId",
                            "customer.externalId",
                            "invoice.invoiceNumber",
                            "invoice.invoiceDate",
                            "invoice.dueDate",
                            "invoice.amount",
                            "invoice.currency"
                        },
                        new String[] {
                            "TRIM",
                            "TRIM",
                            "TRIM",
                            "DATE_PARSE",
                            "DATE_PARSE",
                            "DECIMAL_PARSE",
                            "TRIM"
                        });
        SourceFixture paymentSource =
                source(
                        tenant.getId(),
                        clientId,
                        "PAYMENT",
                        new String[] {
                            "Payment",
                            "Customer",
                            "PaymentDate",
                            "Amount",
                            "Currency",
                            "Reference",
                            "Source"
                        },
                        new String[] {
                            "payment.externalId",
                            "customer.externalId",
                            "payment.paymentDate",
                            "payment.amount",
                            "payment.currency",
                            "payment.reference",
                            "payment.source"
                        },
                        new String[] {
                            "TRIM", "TRIM", "DATE_PARSE", "DECIMAL_PARSE", "TRIM", "TRIM", "TRIM"
                        });

        byte[] customerBody =
                ("Customer;DisplayName\n" + customerExternalId + ";Rabbit Customer\n")
                        .getBytes(StandardCharsets.UTF_8);
        var customerReservation =
                ingestAndAwait(
                        tenant.getId(),
                        serviceToken.accessToken(),
                        customerSource.code(),
                        "customer-key",
                        customerBody);
        var customerReplay =
                ingest(
                        serviceToken.accessToken(),
                        customerSource.code(),
                        "customer-key",
                        "customer-replay",
                        customerBody);
        assertThat(customerReplay.ingestionId()).isEqualTo(customerReservation.ingestionId());
        assertThat(customerReplay.replayed()).isTrue();

        var customer = customers.findByExternalId(tenant.getId(), customerExternalId).orElseThrow();
        assertThat(customer.getTenantId()).isEqualTo(tenant.getId());
        assertThat(customer.getExternalId()).isEqualTo(customerExternalId);
        customers.addEmail(
                tenant.getId(), customer.getId(), "rabbit-smoke@example.test", "WORK", true);

        byte[] invoiceBody =
                ("Invoice;Customer;Number;InvoiceDate;DueDate;Amount;Currency\n"
                                + invoiceExternalId
                                + ";"
                                + customerExternalId
                                + ";RABBIT-INV;"
                                + businessDate.minusDays(20)
                                + ";"
                                + businessDate.minusDays(10)
                                + ";1000.0000;KZT\n")
                        .getBytes(StandardCharsets.UTF_8);
        ingestAndAwait(
                tenant.getId(),
                serviceToken.accessToken(),
                invoiceSource.code(),
                "invoice-key",
                invoiceBody);
        var invoice =
                receivables
                        .findInvoiceByExternalId(tenant.getId(), invoiceExternalId)
                        .orElseThrow();
        assertThat(invoice.getTenantId()).isEqualTo(tenant.getId());
        assertThat(invoice.getCustomerId()).isEqualTo(customer.getId());
        assertThat(invoice.getExternalId()).isEqualTo(invoiceExternalId);
        assertThat(invoice.getOutstandingAmount()).isEqualByComparingTo("1000.0000");

        byte[] paymentBody =
                ("Payment;Customer;PaymentDate;Amount;Currency;Reference;Source\n"
                                + paymentExternalId
                                + ";"
                                + customerExternalId
                                + ";"
                                + businessDate
                                + ";100.0000;KZT;rabbit-smoke;TEST\n")
                        .getBytes(StandardCharsets.UTF_8);
        ingestAndAwait(
                tenant.getId(),
                serviceToken.accessToken(),
                paymentSource.code(),
                "payment-key",
                paymentBody);
        var payment =
                receivables
                        .findPaymentByExternalId(tenant.getId(), paymentExternalId)
                        .orElseThrow();
        assertThat(payment.getTenantId()).isEqualTo(tenant.getId());
        assertThat(payment.getCustomerId()).isEqualTo(customer.getId());
        assertThat(payment.getExternalId()).isEqualTo(paymentExternalId);
        assertThat(payment.getAmount()).isEqualByComparingTo("100.0000");
        UUID allocationCommand = UUID.randomUUID();
        var allocation =
                receivables.allocate(
                        tenant.getId(),
                        payment.getId(),
                        allocationCommand,
                        invoice.getId(),
                        new BigDecimal("100.0000"));
        assertThat(
                        receivables
                                .allocate(
                                        tenant.getId(),
                                        payment.getId(),
                                        allocationCommand,
                                        invoice.getId(),
                                        new BigDecimal("100.0000"))
                                .getId())
                .isEqualTo(allocation.getId());
        assertThat(receivables.invoice(tenant.getId(), invoice.getId()).getOutstandingAmount())
                .isEqualByComparingTo("900.0000");

        var reversed =
                receivables.reverseAllocation(
                        tenant.getId(),
                        payment.getId(),
                        allocation.getId(),
                        allocation.getVersion(),
                        "golden-journey-reversal",
                        "golden-journey");
        assertThat(reversed.getStatus().name()).isEqualTo("REVERSED");
        assertThat(receivables.invoice(tenant.getId(), invoice.getId()).getOutstandingAmount())
                .isEqualByComparingTo("1000.0000");

        var replacementAllocation =
                receivables.allocate(
                        tenant.getId(),
                        payment.getId(),
                        UUID.randomUUID(),
                        invoice.getId(),
                        new BigDecimal("100.0000"));
        assertThat(replacementAllocation.getAmount()).isEqualByComparingTo("100.0000");
        assertThat(receivables.invoice(tenant.getId(), invoice.getId()).getOutstandingAmount())
                .isEqualByComparingTo("900.0000");

        var collectionCase =
                collections.createCase(
                        tenant.getId(),
                        customer.getId(),
                        invoice.getId(),
                        CollectionPriority.HIGH,
                        null,
                        "golden-journey");
        collectionCase =
                collections.start(
                        tenant.getId(),
                        collectionCase.getId(),
                        collectionCase.getVersion(),
                        "golden-journey");
        var action =
                collections.createAction(
                        tenant.getId(),
                        collectionCase.getId(),
                        "CALL",
                        "Rabbit golden journey",
                        Instant.now().plus(Duration.ofDays(1)),
                        CollectionPriority.NORMAL,
                        "golden-journey");
        collections.completeAction(
                tenant.getId(),
                collectionCase.getId(),
                action.getId(),
                action.getVersion(),
                "golden-journey");

        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "RABBIT_" + UUID.randomUUID(),
                                "Rabbit",
                                "NOTIFICATION"));
        TemplateVersion version =
                new TemplateVersion(
                        template.getId(),
                        1,
                        "ru",
                        TemplateChannel.EMAIL,
                        "Rabbit smoke",
                        "<p>Hello {{customer.name}}</p>",
                        null);
        version.validated();
        version.publish();
        version = versions.saveAndFlush(version);

        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "Rabbit smoke",
                        version.getId(),
                        CommunicationChannel.EMAIL.name(),
                        null,
                        new CampaignSelection(
                                Set.of(customer.getId()), Set.of(), 1, 60, null, null),
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
        assertThat(
                        materializer
                                .materializeNextBatch(tenant.getId(), prepared.runId(), 100)
                                .queued())
                .isEqualTo(1);

        var recipients =
                campaignRecipients.findAllByTenantIdAndRunIdOrderByCreatedAtAsc(
                        tenant.getId(), prepared.runId());
        assertThat(recipients).hasSize(1);
        assertThat(recipients.get(0).getCustomerId()).isEqualTo(customer.getId());
        assertThat(recipients.get(0).getInvoiceId()).isEqualTo(invoice.getId());
        assertThat(recipients.get(0).getDestination()).isEqualTo("rabbit-smoke@example.test");

        Message queued =
                messages.findAllByTenantIdAndCampaignRunId(
                                tenant.getId(), prepared.runId(), PageRequest.of(0, 10))
                        .getContent()
                        .get(0);
        assertThat(queued.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(queued.getTenantId()).isEqualTo(tenant.getId());
        assertThat(queued.getCustomerId()).isEqualTo(customer.getId());
        assertThat(queued.getDeliveryRequestedAt()).isNotNull();
        assertThat(deliveryRequestCount(tenant.getId(), queued.getId())).isOne();

        outbox.publishPending();

        UUID messageId = queued.getId();
        await().atMost(ASYNC_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(
                        () -> {
                            outbox.publishPending();
                            Message delivered = messages.findById(messageId).orElseThrow();
                            assertThat(delivered.getStatus()).isEqualTo(MessageStatus.SENT);
                            assertThat(delivered.getProviderMessageId())
                                    .isEqualTo("simulated:email:" + messageId);
                            assertThat(deliveryRequestCount(tenant.getId(), messageId)).isOne();
                        });

        var financial = financialAnalytics.summary(tenant.getId(), businessDate, businessDate);
        assertThat(financial.currencies())
                .singleElement()
                .satisfies(
                        metric -> {
                            assertThat(metric.currency()).isEqualTo("KZT");
                            assertThat(metric.payments()).isEqualByComparingTo("100.0000");
                            assertThat(metric.allocated()).isEqualByComparingTo("200.0000");
                            assertThat(metric.reversedAllocations())
                                    .isEqualByComparingTo("100.0000");
                            assertThat(metric.currentSnapshot().outstanding())
                                    .isEqualByComparingTo("900.0000");
                        });

        Instant analyticsFrom = Instant.now().minus(Duration.ofMinutes(10));
        Instant analyticsTo = Instant.now().plus(Duration.ofMinutes(1));
        var communication =
                communicationAnalytics.summary(
                        CommunicationAnalyticsQueryService.Scope.tenant(tenant.getId()),
                        new CommunicationAnalyticsQueryService.Filter(
                                analyticsFrom,
                                analyticsTo,
                                campaign.getId(),
                                prepared.runId(),
                                CommunicationChannel.EMAIL.name(),
                                null));
        assertThat(communication.business().recipients()).isOne();
        assertThat(communication.business().sent()).isOne();
        assertThat(communication.business().failed()).isZero();
        assertThat(communication.messages().sent()).isOne();
        assertThat(communication.messages().failed()).isZero();
    }

    private long deliveryRequestCount(UUID tenantId, UUID messageId) {
        Long count =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from outbox_events
                        where tenant_id = ?
                          and aggregate_type = 'MESSAGE'
                          and aggregate_id = ?
                          and event_type = 'MESSAGE_DELIVERY_REQUESTED'
                        """,
                        Long.class,
                        tenantId,
                        messageId);
        return count == null ? 0 : count;
    }

    private IngestionApplicationService.Reservation ingestAndAwait(
            UUID tenantId, String accessToken, String sourceCode, String key, byte[] body)
            throws Exception {
        var reservation = ingest(accessToken, sourceCode, key, "golden-journey", body);
        ingestionWorker.process(tenantId, reservation.ingestionId());
        await().atMost(ASYNC_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(
                        () -> {
                            outbox.publishPending();
                            var result =
                                    mockMvc.perform(
                                                    org.springframework.test.web.servlet.request
                                                            .MockMvcRequestBuilders.get(
                                                                    "/api/v1/integration/sources/{sourceCode}/ingestions/{id}",
                                                                    sourceCode,
                                                                    reservation.ingestionId())
                                                            .header(
                                                                    "Authorization",
                                                                    "Bearer " + accessToken))
                                            .andExpect(status().isOk())
                                            .andReturn();
                            var current =
                                    json.readValue(
                                            result.getResponse().getContentAsByteArray(),
                                            IngestionApplicationService.Reservation.class);
                            assertThat(current.status())
                                    .isIn(
                                            IngestionStatus.COMPLETED.name(),
                                            IngestionStatus.PARTIALLY_COMPLETED.name());
                        });
        return reservation;
    }

    private IngestionApplicationService.Reservation ingest(
            String accessToken, String sourceCode, String key, String requestId, byte[] body)
            throws Exception {
        var result =
                mockMvc.perform(
                                post(
                                                "/api/v1/integration/sources/{sourceCode}/ingestions",
                                                sourceCode)
                                        .header("Authorization", "Bearer " + accessToken)
                                        .header("Idempotency-Key", key)
                                        .header("X-Request-Id", requestId)
                                        .contentType("text/csv")
                                        .content(body))
                        .andExpect(status().isAccepted())
                        .andReturn();
        return json.readValue(
                result.getResponse().getContentAsByteArray(),
                IngestionApplicationService.Reservation.class);
    }

    private SourceFixture source(
            UUID tenantId,
            UUID clientId,
            String documentType,
            String[] sourcePaths,
            String[] targetKeys,
            String[] transforms) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        SourceSchemaDefinition schemaDefinition =
                schemaDefinitions.saveAndFlush(
                        new SourceSchemaDefinition(
                                tenantId, "GJ_S_" + suffix, documentType + " schema"));
        SourceSchema schema =
                schemas.saveAndFlush(
                        new SourceSchema(
                                tenantId,
                                schemaDefinition.getId(),
                                schemaDefinition.getCode(),
                                documentType + " schema",
                                SourceFormat.CSV,
                                1));
        SourceField[] fields = new SourceField[sourcePaths.length];
        for (int i = 0; i < sourcePaths.length; i++) {
            fields[i] =
                    sourceFields.saveAndFlush(
                            new SourceField(
                                    schema.getId(),
                                    sourcePaths[i],
                                    "STRING",
                                    null,
                                    true,
                                    i + 1,
                                    SourceFieldScope.DOCUMENT,
                                    i == 0,
                                    FieldValuePolicy.REQUIRE_SAME));
        }
        schema.validated();
        schema.publish();
        schemas.saveAndFlush(schema);

        MappingProfileDefinition mappingDefinition =
                mappingDefinitions.saveAndFlush(
                        new MappingProfileDefinition(
                                tenantId,
                                "GJ_M_" + suffix,
                                documentType + " mapping",
                                documentType));
        MappingProfile mapping =
                mappings.saveAndFlush(
                        new MappingProfile(
                                tenantId,
                                mappingDefinition.getId(),
                                schema.getId(),
                                mappingDefinition.getCode(),
                                documentType + " mapping",
                                documentType,
                                1));
        mapping.validated();
        mapping.publish();
        mappings.saveAndFlush(mapping);
        for (int i = 0; i < fields.length; i++) {
            int index = i;
            FieldDefinition target =
                    fieldDefinitions.findAvailable(null).stream()
                            .filter(value -> value.getKey().equals(targetKeys[index]))
                            .findFirst()
                            .orElseThrow();
            var config = json.createObjectNode().put("type", transforms[i]);
            if ("DATE_PARSE".equals(transforms[i])) config.put("pattern", "yyyy-MM-dd");
            if ("DECIMAL_PARSE".equals(transforms[i])) config.put("decimalSeparator", ".");
            mappingRules.saveAndFlush(
                    new MappingRule(
                            mapping.getId(),
                            fields[i].getId(),
                            target.getId(),
                            config,
                            null,
                            true));
        }

        String code = "gj-" + documentType.toLowerCase() + "-" + suffix.toLowerCase();
        var created =
                integrationSources.create(
                        tenantId,
                        new IntegrationSourceService.CreateCommand(
                                code,
                                documentType + " golden source",
                                clientId,
                                schemaDefinition.getId(),
                                mappingDefinition.getId(),
                                "STANDARD",
                                json.createObjectNode(),
                                json.createObjectNode(),
                                json.createObjectNode()));
        assertThat(integrationSources.validate(tenantId, created.id()).ready()).isTrue();
        var active = integrationSources.activate(tenantId, created.id(), created.version());
        assertThat(active.status()).isEqualTo("ACTIVE");
        return new SourceFixture(code);
    }

    private record SourceFixture(String code) {}
}
