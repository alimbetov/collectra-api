package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignMessageMaterializer;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import io.collectra.api.shared.outbox.OutboxPublisher;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.time.Duration;
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
    @Autowired CustomerService customers;
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
