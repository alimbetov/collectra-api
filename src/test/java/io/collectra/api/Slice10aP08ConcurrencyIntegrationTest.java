package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.application.DeliveryCommand;
import io.collectra.api.communication.application.DeliveryGateway;
import io.collectra.api.communication.application.DeliveryResult;
import io.collectra.api.communication.application.MessageDeliveryRequested;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.communication.infrastructure.messaging.MessageDeliveryListener;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(Slice10aP08ConcurrencyIntegrationTest.Configuration.class)
@SpringBootTest(properties = "collectra.communication.delivery.enabled=true")
class Slice10aP08ConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-12T18:00:00Z");

    @Autowired TenantRepository tenants;
    @Autowired DocumentTemplateRepository documentTemplates;
    @Autowired TemplateVersionRepository templateVersions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CampaignService campaigns;
    @Autowired CampaignRunRepository runs;
    @Autowired MessageRepository messages;
    @Autowired MessageDeliveryListener listener;
    @Autowired BlockingDeliveryGateway gateway;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;

    @BeforeEach
    void resetGateway() {
        gateway.reset();
    }

    @Test
    void eightWorkersAndOneHundredDuplicateEventsProduceExactlyOnePhysicalDelivery()
            throws Exception {
        Fixture fixture = fixture();
        MessageDeliveryRequested duplicateEvent =
                new MessageDeliveryRequested(fixture.tenantId(), fixture.messageId());

        int duplicateCount = 100;
        int workerCount = 8;
        CountDownLatch submittedToListener = new CountDownLatch(duplicateCount);
        var executor = Executors.newFixedThreadPool(workerCount);
        List<Future<?>> futures = new ArrayList<>(duplicateCount);

        try {
            for (int i = 0; i < duplicateCount; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    submittedToListener.countDown();
                                    listener.consume(duplicateEvent);
                                }));
            }

            assertThat(gateway.awaitFirstProviderCall(10, TimeUnit.SECONDS)).isTrue();
            assertThat(submittedToListener.await(10, TimeUnit.SECONDS)).isTrue();
            gateway.allowProviderResponse();

            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            gateway.allowProviderResponse();
            executor.shutdownNow();
        }

        Message persisted = messages.findById(fixture.messageId()).orElseThrow();
        CampaignRun run = runs.findById(fixture.runId()).orElseThrow();

        assertThat(gateway.deliveryCount()).isOne();
        assertThat(persisted.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(persisted.getAttemptCount()).isOne();
        assertThat(persisted.getProviderMessageId()).isEqualTo("p08-provider-1");
        assertThat(run.getSentCount()).isOne();
        assertThat(run.getFailedCount()).isZero();
        assertThat(run.getRetryCount()).isZero();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Tenant tenant = tenants.saveAndFlush(new Tenant("p08-" + suffix, "P08 concurrency test"));

        DocumentTemplate documentTemplate =
                documentTemplates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "P08_REMINDER_" + suffix,
                                "P08 payment reminder",
                                "INVOICE"));
        TemplateVersion templateVersion =
                new TemplateVersion(
                        documentTemplate.getId(),
                        1,
                        "ru-KZ",
                        TemplateChannel.EMAIL,
                        "Payment reminder",
                        "<p>Please pay your invoice</p>",
                        null);
        templateVersion.validated();
        templateVersion.publish();
        templateVersion = templateVersions.saveAndFlush(templateVersion);

        Customer customer =
                customers.create(
                        tenant.getId(),
                        "customer-" + suffix,
                        CustomerType.INDIVIDUAL,
                        "P08 Customer",
                        "P08",
                        "Customer",
                        null,
                        null,
                        null,
                        "ru-KZ",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), customer.getId(), "p08@example.test", "WORK", true);

        LocalDate today = LocalDate.now(clock);
        Invoice invoice =
                receivables.createInvoice(
                        tenant.getId(),
                        customer.getId(),
                        null,
                        "invoice-" + suffix,
                        "P08-INV-" + suffix,
                        today.minusDays(20),
                        today.minusDays(10),
                        new BigDecimal("10000.00"),
                        "KZT",
                        null,
                        json.createObjectNode());

        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "P08 duplicate delivery",
                        templateVersion.getId(),
                        "EMAIL",
                        null,
                        new CampaignSelection(Set.of(), Set.of(), 1, 30, null, null),
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
        CampaignRun run = campaigns.run(tenant.getId(), prepared.runId());
        run.start(NOW);
        run = runs.saveAndFlush(run);
        CampaignRecipient recipient = campaigns.recipients(tenant.getId(), prepared.runId()).get(0);

        Message message =
                messages.saveAndFlush(
                        Message.queued(
                                tenant.getId(),
                                campaign.getId(),
                                run.getId(),
                                recipient.getId(),
                                customer.getId(),
                                invoice.getId(),
                                templateVersion.getId(),
                                CommunicationChannel.EMAIL,
                                recipient.getDestination(),
                                templateVersion.getLocale(),
                                templateVersion.getSubject(),
                                templateVersion.getContentHtml()));

        return new Fixture(tenant.getId(), message.getId(), run.getId());
    }

    private record Fixture(UUID tenantId, UUID messageId, UUID runId) {}

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        Clock p08Clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        BlockingDeliveryGateway p08DeliveryGateway() {
            return new BlockingDeliveryGateway();
        }
    }

    static class BlockingDeliveryGateway implements DeliveryGateway {
        private final AtomicInteger deliveryCount = new AtomicInteger();
        private volatile CountDownLatch providerEntered = new CountDownLatch(1);
        private volatile CountDownLatch providerMayReturn = new CountDownLatch(1);

        @Override
        public DeliveryResult deliver(DeliveryCommand command) {
            int call = deliveryCount.incrementAndGet();
            providerEntered.countDown();
            try {
                if (!providerMayReturn.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for P08 provider release");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for P08 provider release", exception);
            }
            return new DeliveryResult.Accepted("p08-provider-" + call);
        }

        boolean awaitFirstProviderCall(long timeout, TimeUnit unit) throws InterruptedException {
            return providerEntered.await(timeout, unit);
        }

        void allowProviderResponse() {
            providerMayReturn.countDown();
        }

        int deliveryCount() {
            return deliveryCount.get();
        }

        void reset() {
            deliveryCount.set(0);
            providerEntered = new CountDownLatch(1);
            providerMayReturn = new CountDownLatch(1);
        }
    }
}
