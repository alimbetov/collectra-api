package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignMessageMaterializer;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.collection.application.CollectionService;
import io.collectra.api.collection.domain.CollectionPriority;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.integration.application.ServiceClientService;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.shared.outbox.OutboxPublisher;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

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
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CollectionService collections;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CampaignService campaigns;
    @Autowired CampaignMessageMaterializer materializer;
    @Autowired MessageRepository messages;
    @Autowired OutboxPublisher outbox;
    @Autowired ObjectMapper json;

    @Test
    void outboxTraversesRealRabbitTopologyIntoDeliveryWorker() {
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
                        Set.of("integration:imports:create"));
        assertThat(serviceToken.accessToken()).isNotBlank();

        var customer =
                customers.create(
                        tenant.getId(),
                        "RABBIT-" + UUID.randomUUID(),
                        CustomerType.INDIVIDUAL,
                        "Rabbit Customer",
                        "Rabbit",
                        "Customer",
                        null,
                        null,
                        null,
                        "ru",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(
                tenant.getId(), customer.getId(), "rabbit-smoke@example.test", "WORK", true);

        LocalDate businessDate = LocalDate.now();
        var invoice =
                receivables.createInvoice(
                        tenant.getId(),
                        customer.getId(),
                        null,
                        "RABBIT-INV-" + UUID.randomUUID(),
                        "RABBIT-INV",
                        businessDate.minusDays(20),
                        businessDate.minusDays(10),
                        new BigDecimal("1000.0000"),
                        "KZT",
                        null,
                        json.createObjectNode());
        var payment =
                receivables.createPayment(
                        tenant.getId(),
                        customer.getId(),
                        "RABBIT-PAY-" + UUID.randomUUID(),
                        businessDate,
                        new BigDecimal("100.0000"),
                        "KZT",
                        "rabbit-smoke",
                        "TEST",
                        json.createObjectNode());
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
                        CampaignSelection.customer(Set.of(customer.getId()), Set.of()),
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
        assertThat(
                        materializer
                                .materializeNextBatch(tenant.getId(), prepared.runId(), 100)
                                .queued())
                .isEqualTo(1);

        Message queued =
                messages.findAllByTenantIdAndCampaignRunId(
                                tenant.getId(), prepared.runId(), PageRequest.of(0, 10))
                        .getContent()
                        .get(0);
        assertThat(queued.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(queued.getDeliveryRequestedAt()).isNotNull();

        outbox.publishPending();

        UUID messageId = queued.getId();
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(
                        () -> {
                            Message delivered = messages.findById(messageId).orElseThrow();
                            assertThat(delivered.getStatus()).isEqualTo(MessageStatus.SENT);
                            assertThat(delivered.getProviderMessageId())
                                    .isEqualTo("simulated:email:" + messageId);
                        });
    }
}
