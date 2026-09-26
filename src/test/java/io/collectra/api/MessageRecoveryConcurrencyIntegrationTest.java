package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.domain.CampaignRunStatus;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.application.MessageStateService;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageRepository;
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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(MessageRecoveryConcurrencyIntegrationTest.FixedClockConfiguration.class)
class MessageRecoveryConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");

    @Autowired TenantRepository tenants;
    @Autowired DocumentTemplateRepository documentTemplates;
    @Autowired TemplateVersionRepository templateVersions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CampaignService campaigns;
    @Autowired CampaignRunRepository runs;
    @Autowired MessageRepository messages;
    @Autowired MessageStateService states;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    @Test
    void staleRecoveryRacingLateAcceptanceProducesOneLegalTerminalTransition() throws Exception {
        Fixture fixture = fixture();
        assertThat(states.begin(fixture.tenantId(), fixture.messageId())).isPresent();
        assertThat(states.beginProviderAttempt(fixture.tenantId(), fixture.messageId()))
                .isPresent();
        jdbc.update(
                "UPDATE messages SET processing_started_at = ? WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(360)),
                fixture.messageId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var recovery =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                return states.recoverStale(
                                        fixture.tenantId(),
                                        fixture.messageId(),
                                        NOW.minusSeconds(300),
                                        NOW);
                            });
            var lateAcceptance =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                return states.markSent(
                                        fixture.tenantId(), fixture.messageId(), "provider-late");
                            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            recovery.get(10, TimeUnit.SECONDS);
            assertThat(lateAcceptance.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        Message message = messages.findById(fixture.messageId()).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(message.getProviderMessageId()).isEqualTo("provider-late");
        assertThat(message.getNextRetryAt()).isNull();

        CampaignRun run = runs.findById(fixture.runId()).orElseThrow();
        assertThat(run.getSentCount()).isOne();
        assertThat(run.getFailedCount()).isZero();
        assertThat(run.getRetryCount()).isZero();
        assertThat(run.getStatus()).isEqualTo(CampaignRunStatus.COMPLETED);

        assertThat(states.markSent(fixture.tenantId(), fixture.messageId(), "provider-late"))
                .isFalse();
        assertThat(runs.findById(fixture.runId()).orElseThrow().getSentCount()).isOne();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("recovery-race-" + suffix, "Recovery concurrency test"));
        DocumentTemplate template =
                documentTemplates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "RECOVERY_" + suffix,
                                "Recovery reminder",
                                "INVOICE"));
        TemplateVersion version =
                new TemplateVersion(
                        template.getId(),
                        1,
                        "ru-KZ",
                        TemplateChannel.EMAIL,
                        "Recovery reminder",
                        "<p>Please pay your invoice</p>",
                        null);
        version.validated();
        version.publish();
        version = templateVersions.saveAndFlush(version);

        Customer customer =
                customers.create(
                        tenant.getId(),
                        "customer-" + suffix,
                        CustomerType.INDIVIDUAL,
                        "Recovery Customer",
                        "Recovery",
                        "Customer",
                        null,
                        null,
                        null,
                        "ru-KZ",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), customer.getId(), "recovery@example.com", "WORK", true);

        LocalDate today = LocalDate.now(clock);
        Invoice invoice =
                receivables.createInvoice(
                        tenant.getId(),
                        customer.getId(),
                        null,
                        "invoice-" + suffix,
                        "INV-" + suffix,
                        today.minusDays(20),
                        today.minusDays(10),
                        new BigDecimal("10000.00"),
                        "KZT",
                        null,
                        json.createObjectNode());

        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "Recovery race campaign",
                        version.getId(),
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
                                version.getId(),
                                CommunicationChannel.EMAIL,
                                recipient.getDestination(),
                                version.getLocale(),
                                version.getSubject(),
                                version.getContentHtml()));
        return new Fixture(tenant.getId(), message.getId(), run.getId());
    }

    private record Fixture(UUID tenantId, UUID messageId, UUID runId) {}

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
