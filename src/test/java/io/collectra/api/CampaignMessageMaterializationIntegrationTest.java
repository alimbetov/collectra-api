package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignMessageMaterializer;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.CampaignRunStatus;
import io.collectra.api.campaign.domain.CampaignRunTemplateBinding;
import io.collectra.api.campaign.infrastructure.CampaignRunTemplateBindingRepository;
import io.collectra.api.communication.application.MessageDeliveryRequested;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.shared.outbox.OutboxRepository;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;

@Import(CampaignMessageMaterializationIntegrationTest.FixedClockConfiguration.class)
class CampaignMessageMaterializationIntegrationTest extends AbstractIntegrationTest {
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-12T00:00:00Z");

    @Autowired TenantRepository tenants;
    @Autowired TenantLocaleRepository tenantLocales;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CampaignService campaigns;
    @Autowired CampaignMessageMaterializer materializer;
    @Autowired CampaignRunTemplateBindingRepository bindings;
    @Autowired MessageRepository messages;
    @Autowired OutboxRepository outbox;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;

    @Test
    void materializationSnapshotsTemplateVersionAcrossBatchesAndIsIdempotent() {
        Fixture fixture = prepareCampaign(2);

        TemplateVersion version2 =
                new TemplateVersion(
                        fixture.template().getId(),
                        2,
                        "ru",
                        TemplateChannel.EMAIL,
                        "Reminder v2",
                        "<p>Version two</p>",
                        null);
        version2.validated();
        version2 = versions.saveAndFlush(version2);

        var first = materializer.materializeNextBatch(fixture.tenant().getId(), fixture.runId(), 1);

        assertThat(first.selected()).isEqualTo(1);
        assertThat(first.queued()).isEqualTo(1);
        assertThat(first.skipped()).isZero();
        assertThat(first.hasNext()).isTrue();
        assertThat(campaigns.run(fixture.tenant().getId(), fixture.runId()).getStatus())
                .isEqualTo(CampaignRunStatus.RUNNING);

        List<CampaignRunTemplateBinding> runBindings =
                bindings.findAllByTenantIdAndCampaignRunId(
                        fixture.tenant().getId(), fixture.runId());
        assertThat(runBindings)
                .singleElement()
                .satisfies(
                        binding -> {
                            assertThat(binding.getRequestedLocale()).isEqualTo("ru");
                            assertThat(binding.getResolvedLocale()).isEqualTo("ru");
                            assertThat(binding.getTemplateVersionId())
                                    .isEqualTo(fixture.version1().getId());
                        });

        version2.publish();
        versions.saveAndFlush(version2);

        var second =
                materializer.materializeNextBatch(fixture.tenant().getId(), fixture.runId(), 1);
        var retry = materializer.materializeNextBatch(fixture.tenant().getId(), fixture.runId(), 1);

        assertThat(second.selected()).isEqualTo(1);
        assertThat(second.queued()).isEqualTo(1);
        assertThat(second.skipped()).isZero();
        assertThat(second.hasNext()).isFalse();
        assertThat(retry.selected()).isZero();
        assertThat(retry.queued()).isZero();
        assertThat(retry.skipped()).isZero();
        assertThat(retry.hasNext()).isFalse();

        List<Message> materialized =
                messages.findAllByTenantIdAndCampaignRunId(
                                fixture.tenant().getId(), fixture.runId(), PageRequest.of(0, 10))
                        .getContent();
        assertThat(materialized).hasSize(2);
        assertThat(materialized)
                .allSatisfy(
                        message -> {
                            assertThat(message.getTemplateVersionId())
                                    .isEqualTo(fixture.version1().getId());
                            assertThat(message.getStatus()).isEqualTo(MessageStatus.QUEUED);
                            assertThat(message.getResolvedLocale()).isEqualTo("ru");
                            assertThat(message.getDestination()).isEqualTo("customer@example.com");
                            assertThat(message.getSubject()).isEqualTo("Reminder v1");
                            assertThat(message.getBody()).contains("Version one");
                        });

        assertThat(
                        outbox.findAll().stream()
                                .filter(
                                        event ->
                                                fixture.tenant()
                                                        .getId()
                                                        .equals(event.getTenantId()))
                                .filter(
                                        event ->
                                                MessageDeliveryRequested.EVENT_TYPE.equals(
                                                        event.getEventType())))
                .hasSize(2)
                .allSatisfy(
                        event -> {
                            assertThat(event.getAggregateType())
                                    .isEqualTo(MessageDeliveryRequested.AGGREGATE_TYPE);
                            assertThat(materialized)
                                    .extracting(Message::getId)
                                    .contains(event.getAggregateId());
                        });
    }

    @Test
    void finalEligibilitySkipsPaidRecipientWithoutCreatingMessageOrDeliveryEvent() {
        Fixture fixture = prepareCampaign(1);
        var invoice = fixture.invoices().get(0);
        LocalDate today = LocalDate.now(clock);

        var payment =
                receivables.createPayment(
                        fixture.tenant().getId(),
                        fixture.customerId(),
                        "payment-" + UUID.randomUUID(),
                        today,
                        invoice.getOriginalAmount(),
                        "KZT",
                        "paid before materialization",
                        "TEST",
                        json.createObjectNode());
        receivables.allocate(
                fixture.tenant().getId(),
                payment.getId(),
                invoice.getId(),
                invoice.getOriginalAmount());

        var result =
                materializer.materializeNextBatch(fixture.tenant().getId(), fixture.runId(), 100);

        assertThat(result.selected()).isEqualTo(1);
        assertThat(result.queued()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(campaigns.recipients(fixture.tenant().getId(), fixture.runId()))
                .singleElement()
                .satisfies(
                        recipient -> {
                            assertThat(recipient.getStatus().name()).isEqualTo("SKIPPED");
                            assertThat(recipient.getSkipReason()).isEqualTo("PAID");
                        });
        assertThat(
                        messages.findAllByTenantIdAndCampaignRunId(
                                        fixture.tenant().getId(),
                                        fixture.runId(),
                                        PageRequest.of(0, 10))
                                .getContent())
                .isEmpty();
        assertThat(
                        outbox.findAll().stream()
                                .filter(
                                        event ->
                                                fixture.tenant()
                                                        .getId()
                                                        .equals(event.getTenantId()))
                                .filter(
                                        event ->
                                                MessageDeliveryRequested.EVENT_TYPE.equals(
                                                        event.getEventType())))
                .isEmpty();
    }

    private Fixture prepareCampaign(int invoiceCount) {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant(
                                "message-materialization-" + UUID.randomUUID(),
                                "Message Materialization"));
        tenantLocales.saveAndFlush(new TenantLocale(tenant.getId(), "ru", true, true, 0));

        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(), "DEBT_REMINDER", "Debt reminder", "INVOICE"));
        TemplateVersion version1 =
                new TemplateVersion(
                        template.getId(),
                        1,
                        "ru",
                        TemplateChannel.EMAIL,
                        "Reminder v1",
                        "<p>Version one</p>",
                        null);
        version1.validated();
        version1.publish();
        version1 = versions.saveAndFlush(version1);

        var customer =
                customers.create(
                        tenant.getId(),
                        "customer-" + UUID.randomUUID(),
                        CustomerType.INDIVIDUAL,
                        "Test Customer",
                        "Test",
                        "Customer",
                        null,
                        null,
                        null,
                        "ru",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), customer.getId(), "customer@example.com", "WORK", true);

        LocalDate today = LocalDate.now(clock);
        java.util.ArrayList<io.collectra.api.receivable.domain.Invoice> invoices =
                new java.util.ArrayList<>();
        for (int index = 1; index <= invoiceCount; index++) {
            invoices.add(
                    receivables.createInvoice(
                            tenant.getId(),
                            customer.getId(),
                            null,
                            "invoice-" + UUID.randomUUID(),
                            "INV-" + index + "-" + UUID.randomUUID(),
                            today.minusDays(20),
                            today.minusDays(10),
                            new BigDecimal("10000.00"),
                            "KZT",
                            null,
                            json.createObjectNode()));
        }

        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "Overdue debt reminder",
                        version1.getId(),
                        "EMAIL",
                        null,
                        new CampaignSelection(Set.of(), Set.of(), 1, 30, null, null),
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
        assertThat(prepared.recipients()).isEqualTo(invoiceCount);

        return new Fixture(
                tenant,
                template,
                version1,
                customer.getId(),
                List.copyOf(invoices),
                prepared.runId());
    }

    private record Fixture(
            Tenant tenant,
            DocumentTemplate template,
            TemplateVersion version1,
            UUID customerId,
            List<io.collectra.api.receivable.domain.Invoice> invoices,
            UUID runId) {}

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}
