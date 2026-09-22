package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.AudienceSelectionType;
import io.collectra.api.campaign.application.CampaignMessageMaterializer;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.CampaignRecipientStatus;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

class GenericCustomerCampaignAudienceIntegrationTest extends AbstractIntegrationTest {

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
    void customerAudienceIncludesSegmentMemberWithoutInvoice() {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant(
                                "generic-audience-" + UUID.randomUUID(), "Generic Audience Test"));

        tenantLocales.saveAndFlush(new TenantLocale(tenant.getId(), "ru", true, true, 0));

        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "GENERIC_NOTICE",
                                "Generic notice",
                                "NOTIFICATION"));
        TemplateVersion version =
                new TemplateVersion(
                        template.getId(),
                        1,
                        "ru",
                        TemplateChannel.EMAIL,
                        "Notification",
                        "<p>Hello</p>",
                        null);
        version.validated();
        version.publish();
        version = versions.saveAndFlush(version);

        var included =
                customers.create(
                        tenant.getId(),
                        "customer-included-" + UUID.randomUUID(),
                        CustomerType.INDIVIDUAL,
                        "Included Customer",
                        "Included",
                        "Customer",
                        null,
                        null,
                        null,
                        "ru",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), included.getId(), "included@example.com", "WORK", true);

        var outsideSegment =
                customers.create(
                        tenant.getId(),
                        "customer-outside-" + UUID.randomUUID(),
                        CustomerType.INDIVIDUAL,
                        "Outside Customer",
                        "Outside",
                        "Customer",
                        null,
                        null,
                        null,
                        "ru",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(
                tenant.getId(), outsideSegment.getId(), "outside@example.com", "WORK", true);

        var segment =
                customers.createSegment(
                        tenant.getId(),
                        "GENERIC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                        "Generic audience",
                        null);
        customers.addSegment(tenant.getId(), included.getId(), segment.getId());

        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "Generic customer notification",
                        version.getId(),
                        "EMAIL",
                        null,
                        CampaignSelection.customer(Set.of(), Set.of(segment.getId())),
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());

        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());

        assertThat(prepared.recipients()).isEqualTo(1);
        assertThat(campaigns.recipients(tenant.getId(), prepared.runId()))
                .singleElement()
                .satisfies(
                        recipient -> {
                            assertThat(recipient.getCustomerId()).isEqualTo(included.getId());
                            assertThat(recipient.getInvoiceId()).isNull();
                            assertThat(recipient.getChannel()).isEqualTo("EMAIL");
                            assertThat(recipient.getDestination()).isEqualTo("included@example.com");
                            assertThat(recipient.getLocale()).isEqualTo("ru");
                            assertThat(recipient.getStatus())
                                    .isEqualTo(CampaignRecipientStatus.SNAPSHOT);
                        });

        var materialized =
                materializer.materializeNextBatch(tenant.getId(), prepared.runId(), 100);

        assertThat(materialized.selected()).isEqualTo(1);
        assertThat(materialized.queued()).isEqualTo(1);
        assertThat(materialized.skipped()).isZero();
        assertThat(
                        messages.findAllByTenantIdAndCampaignRunId(
                                        tenant.getId(), prepared.runId(), PageRequest.of(0, 10))
                                .getContent())
                .singleElement()
                .satisfies(
                        message -> {
                            assertThat(message.getCustomerId()).isEqualTo(included.getId());
                            assertThat(message.getInvoiceId()).isNull();
                            assertThat(message.getStatus()).isEqualTo(MessageStatus.QUEUED);
                            assertThat(message.getDestination()).isEqualTo("included@example.com");
                            assertThat(message.getSubject()).isEqualTo("Notification");
                            assertThat(message.getBody()).contains("Hello");
                        });
    }

    @Test
    void legacySelectionDefaultsToReceivable() {
        CampaignSelection selection = new CampaignSelection(Set.of(), Set.of(), 1, 30, null, null);

        assertThat(selection.audienceSelectionType()).isEqualTo(AudienceSelectionType.RECEIVABLE);
    }

    @Test
    void customerAudienceRejectsReceivableOnlyFilters() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                new CampaignSelection(
                                        Set.of(),
                                        Set.of(),
                                        1,
                                        null,
                                        null,
                                        null,
                                        AudienceSelectionType.CUSTOMER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support receivable filters");
    }
}
