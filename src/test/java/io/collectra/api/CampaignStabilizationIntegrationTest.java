package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignEligibilityService;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.CampaignRecipientStatus;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.PaymentStatus;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@Import(CampaignStabilizationIntegrationTest.FixedClockConfiguration.class)
class CampaignStabilizationIntegrationTest extends AbstractIntegrationTest {
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-11T00:00:00Z");

    @Autowired TenantRepository tenants;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CampaignService campaigns;
    @Autowired CampaignEligibilityService eligibility;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;

    @Test
    @Transactional
    void prepareThenPaymentAllocationCausesPaidRecipientToBeSkipped() {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("campaign-stabilization-" + UUID.randomUUID(), "Campaign Test"));

        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(), "DEBT_REMINDER", "Debt reminder", "INVOICE"));
        TemplateVersion version =
                new TemplateVersion(
                        template.getId(),
                        1,
                        "ru-KZ",
                        TemplateChannel.EMAIL,
                        "Payment reminder",
                        "<p>Please pay your invoice</p>",
                        null);
        version.validated();
        version.publish();
        version = versions.saveAndFlush(version);

        var customer =
                customers.create(
                        tenant.getId(),
                        "customer-1",
                        CustomerType.INDIVIDUAL,
                        "Test Customer",
                        "Test",
                        "Customer",
                        null,
                        null,
                        null,
                        "ru-KZ",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), customer.getId(), "customer@example.com", "WORK", true);

        LocalDate today = LocalDate.now(clock);
        var invoice =
                receivables.createInvoice(
                        tenant.getId(),
                        customer.getId(),
                        null,
                        "invoice-1",
                        "INV-1",
                        today.minusDays(20),
                        today.minusDays(10),
                        new BigDecimal("10000.00"),
                        "KZT",
                        null,
                        json.createObjectNode());

        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "Overdue debt reminder",
                        version.getId(),
                        "EMAIL",
                        null,
                        new CampaignSelection(Set.of(), Set.of(), 1, 30, null, null),
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());

        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
        assertThat(prepared.recipients()).isEqualTo(1);
        assertThat(campaigns.recipients(tenant.getId(), prepared.runId()))
                .singleElement()
                .satisfies(
                        recipient -> {
                            assertThat(recipient.getStatus())
                                    .isEqualTo(CampaignRecipientStatus.SNAPSHOT);
                            assertThat(recipient.getDestination())
                                    .isEqualTo("customer@example.com");
                        });

        var payment =
                receivables.createPayment(
                        tenant.getId(),
                        customer.getId(),
                        "payment-1",
                        today,
                        new BigDecimal("10000.00"),
                        "KZT",
                        "full payment",
                        "TEST",
                        json.createObjectNode());
        receivables.allocate(
                tenant.getId(), payment.getId(), invoice.getId(), new BigDecimal("10000.00"));

        var recheck = eligibility.recheck(tenant.getId(), prepared.runId());

        assertThat(recheck.total()).isEqualTo(1);
        assertThat(recheck.eligible()).isZero();
        assertThat(recheck.skipped()).isEqualTo(1);
        assertThat(receivables.invoice(tenant.getId(), invoice.getId()).getPaymentStatus())
                .isEqualTo(PaymentStatus.PAID);
        assertThat(campaigns.recipients(tenant.getId(), prepared.runId()))
                .singleElement()
                .satisfies(
                        recipient -> {
                            assertThat(recipient.getStatus())
                                    .isEqualTo(CampaignRecipientStatus.SKIPPED);
                            assertThat(recipient.getSkipReason()).isEqualTo("PAID");
                        });
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}
