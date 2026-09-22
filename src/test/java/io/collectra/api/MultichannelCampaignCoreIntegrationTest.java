package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

class MultichannelCampaignCoreIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenants;
    @Autowired TenantLocaleRepository tenantLocales;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CustomerService customers;
    @Autowired CampaignService campaigns;
    @Autowired CampaignMessageMaterializer materializer;
    @Autowired MessageRepository messages;
    @Autowired ObjectMapper json;

    @Test
    @Transactional
    void smsCampaignMaterializesPlainTextToNormalizedPhone() {
        Fixture fixture = fixture(TemplateChannel.SMS, "Hello {{customer.firstName}}");
        customers.addPhone(
                fixture.tenant().getId(),
                fixture.customerId(),
                "+7 (777) 123-45-67",
                "MOBILE",
                true);

        Message message = run(fixture, CommunicationChannel.SMS);

        assertThat(message.getChannel()).isEqualTo(CommunicationChannel.SMS);
        assertThat(message.getDestination()).isEqualTo("+77771234567");
        assertThat(message.getSubject()).isNull();
        assertThat(message.getBody()).isEqualTo("Hello Test");
        assertThat(message.getStatus()).isEqualTo(MessageStatus.QUEUED);
    }

    @Test
    @Transactional
    void whatsappCampaignUsesPhoneDestination() {
        Fixture fixture = fixture(TemplateChannel.WHATSAPP, "WhatsApp {{customer.displayName}}");
        customers.addPhone(
                fixture.tenant().getId(), fixture.customerId(), "77001234567", "MOBILE", true);

        Message message = run(fixture, CommunicationChannel.WHATSAPP);

        assertThat(message.getChannel()).isEqualTo(CommunicationChannel.WHATSAPP);
        assertThat(message.getDestination()).isEqualTo("+77001234567");
        assertThat(message.getSubject()).isNull();
        assertThat(message.getBody()).isEqualTo("WhatsApp Test Customer");
    }

    @Test
    @Transactional
    void telegramCampaignUsesDurableChannelAddress() {
        Fixture fixture = fixture(TemplateChannel.TELEGRAM, "Telegram {{customer.externalId}}");
        customers.addChannelAddress(
                fixture.tenant().getId(),
                fixture.customerId(),
                CommunicationChannel.TELEGRAM.name(),
                "123456789",
                true,
                Instant.parse("2026-09-22T00:00:00Z"));

        Message message = run(fixture, CommunicationChannel.TELEGRAM);

        assertThat(message.getChannel()).isEqualTo(CommunicationChannel.TELEGRAM);
        assertThat(message.getDestination()).isEqualTo("123456789");
        assertThat(message.getSubject()).isNull();
        assertThat(message.getBody()).startsWith("Telegram customer-");
    }

    @Test
    @Transactional
    void missingChannelDestinationIsSkippedBeforeMessageCreation() {
        Fixture fixture = fixture(TemplateChannel.SMS, "No destination");

        var campaign =
                campaigns.create(
                        fixture.tenant().getId(),
                        "SMS without phone",
                        fixture.versionId(),
                        CommunicationChannel.SMS.name(),
                        null,
                        CampaignSelection.customer(Set.of(fixture.customerId()), Set.of()),
                        null);
        campaigns.activate(fixture.tenant().getId(), campaign.getId());
        var prepared = campaigns.prepare(fixture.tenant().getId(), campaign.getId());

        var materialized =
                materializer.materializeNextBatch(fixture.tenant().getId(), prepared.runId(), 100);

        assertThat(materialized.selected()).isEqualTo(1);
        assertThat(materialized.queued()).isZero();
        assertThat(materialized.skipped()).isEqualTo(1);
        assertThat(campaigns.recipients(fixture.tenant().getId(), prepared.runId()))
                .singleElement()
                .satisfies(
                        recipient -> {
                            assertThat(recipient.getDestination()).isNull();
                            assertThat(recipient.getSkipReason()).isEqualTo("NO_CONTACT");
                        });
        assertThat(
                        messages.findAllByTenantIdAndCampaignRunId(
                                        fixture.tenant().getId(),
                                        prepared.runId(),
                                        PageRequest.of(0, 10))
                                .getContent())
                .isEmpty();
    }

    private Message run(Fixture fixture, CommunicationChannel channel) {
        var campaign =
                campaigns.create(
                        fixture.tenant().getId(),
                        channel + " campaign",
                        fixture.versionId(),
                        channel.name(),
                        null,
                        CampaignSelection.customer(Set.of(fixture.customerId()), Set.of()),
                        null);
        campaigns.activate(fixture.tenant().getId(), campaign.getId());
        var prepared = campaigns.prepare(fixture.tenant().getId(), campaign.getId());

        assertThat(prepared.recipients()).isEqualTo(1);

        var materialized =
                materializer.materializeNextBatch(fixture.tenant().getId(), prepared.runId(), 100);

        assertThat(materialized.selected()).isEqualTo(1);
        assertThat(materialized.queued()).isEqualTo(1);
        assertThat(materialized.skipped()).isZero();

        return messages.findAllByTenantIdAndCampaignRunId(
                        fixture.tenant().getId(), prepared.runId(), PageRequest.of(0, 10))
                .getContent()
                .get(0);
    }

    private Fixture fixture(TemplateChannel channel, String body) {
        String tenantCode =
                "multichannel-" + channel.name().toLowerCase() + "-" + UUID.randomUUID();
        Tenant tenant = tenants.saveAndFlush(new Tenant(tenantCode, "Multichannel Test"));
        tenantLocales.saveAndFlush(new TenantLocale(tenant.getId(), "ru", true, true, 0));

        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "NOTICE_" + channel + "_" + UUID.randomUUID(),
                                channel + " notice",
                                "NOTIFICATION"));
        TemplateVersion version =
                new TemplateVersion(
                        template.getId(),
                        1,
                        "ru",
                        channel,
                        channel == TemplateChannel.EMAIL ? "Subject" : null,
                        body,
                        null);
        version.validated();
        version.publish();
        version = versions.saveAndFlush(version);

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

        return new Fixture(tenant, customer.getId(), version.getId());
    }

    private record Fixture(Tenant tenant, UUID customerId, UUID versionId) {}
}
