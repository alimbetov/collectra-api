package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRun;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(CommunicationMessagePersistenceIntegrationTest.FixedClockConfiguration.class)
class CommunicationMessagePersistenceIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");

    @Autowired TenantRepository tenants;
    @Autowired DocumentTemplateRepository documentTemplates;
    @Autowired TemplateVersionRepository templateVersions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CampaignService campaigns;
    @Autowired MessageRepository messages;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsImmutableSnapshotAndReadsItOnlyInsideTenant() {
        Fixture fixture = fixture();
        Message saved = messages.saveAndFlush(message(fixture));

        Message reloaded =
                messages.findByIdAndTenantId(saved.getId(), fixture.tenant().getId()).orElseThrow();

        assertThat(reloaded.getCampaignId()).isEqualTo(fixture.campaignId());
        assertThat(reloaded.getCampaignRunId()).isEqualTo(fixture.run().getId());
        assertThat(reloaded.getCampaignRecipientId()).isEqualTo(fixture.recipient().getId());
        assertThat(reloaded.getCustomerId()).isEqualTo(fixture.customer().getId());
        assertThat(reloaded.getInvoiceId()).isEqualTo(fixture.invoice().getId());
        assertThat(reloaded.getTemplateVersionId()).isEqualTo(fixture.templateVersion().getId());
        assertThat(reloaded.getChannel()).isEqualTo(CommunicationChannel.EMAIL);
        assertThat(reloaded.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isZero();
        assertThat(fixture.run().getRecipientCount()).isOne();
        assertThat(fixture.run().getPreparedAt()).isEqualTo(NOW);

        assertThat(messages.findByIdAndTenantId(saved.getId(), UUID.randomUUID())).isEmpty();
        assertThat(
                        messages.findByCampaignRecipientIdAndTenantId(
                                fixture.recipient().getId(), UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void enforcesOneMessagePerCampaignRecipient() {
        Fixture fixture = fixture();
        messages.saveAndFlush(message(fixture));

        assertThatThrownBy(() -> messages.saveAndFlush(message(fixture)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void providesTenantScopedPagedRunQueries() {
        Fixture fixture = fixture();
        Message saved = messages.saveAndFlush(message(fixture));

        var slice =
                messages.findAllByTenantIdAndCampaignRunId(
                        fixture.tenant().getId(),
                        fixture.run().getId(),
                        PageRequest.of(
                                0, 1, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));

        assertThat(slice.getContent()).extracting(Message::getId).containsExactly(saved.getId());
        assertThat(
                        messages.countByTenantIdAndCampaignRunIdAndStatus(
                                fixture.tenant().getId(),
                                fixture.run().getId(),
                                MessageStatus.QUEUED))
                .isOne();
        assertThat(
                        messages.findAllByTenantIdAndCampaignRunId(
                                UUID.randomUUID(), fixture.run().getId(), PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void databaseRejectsInvalidAttemptCount() {
        Fixture fixture = fixture();
        Message saved = messages.saveAndFlush(message(fixture));

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE messages SET attempt_count = -1 WHERE id = ?",
                                        saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsStatusWithoutRequiredTimestamp() {
        Fixture fixture = fixture();
        Message saved = messages.saveAndFlush(message(fixture));

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE messages SET status = 'SENT' WHERE id = ?",
                                        saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Tenant tenant =
                tenants.saveAndFlush(new Tenant("message-" + suffix, "Message persistence test"));

        DocumentTemplate documentTemplate =
                documentTemplates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "REMINDER_" + suffix,
                                "Payment reminder",
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
                        "Overdue reminder",
                        templateVersion.getId(),
                        "EMAIL",
                        null,
                        new CampaignSelection(Set.of(), Set.of(), 1, 30, null, null),
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());
        CampaignRun run = campaigns.run(tenant.getId(), prepared.runId());
        CampaignRecipient recipient = campaigns.recipients(tenant.getId(), prepared.runId()).get(0);

        return new Fixture(
                tenant, campaign.getId(), run, recipient, customer, invoice, templateVersion);
    }

    private Message message(Fixture fixture) {
        return Message.queued(
                fixture.tenant().getId(),
                fixture.campaignId(),
                fixture.run().getId(),
                fixture.recipient().getId(),
                fixture.customer().getId(),
                fixture.invoice().getId(),
                fixture.templateVersion().getId(),
                CommunicationChannel.EMAIL,
                fixture.recipient().getDestination(),
                fixture.templateVersion().getLocale(),
                fixture.templateVersion().getSubject(),
                fixture.templateVersion().getContentHtml());
    }

    private record Fixture(
            Tenant tenant,
            UUID campaignId,
            CampaignRun run,
            CampaignRecipient recipient,
            Customer customer,
            Invoice invoice,
            TemplateVersion templateVersion) {}

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
