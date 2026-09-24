package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignFrontendQueryService;
import io.collectra.api.campaign.application.CampaignPreviewService;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.infrastructure.CampaignRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import io.collectra.api.shared.error.BusinessConflictException;
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
import org.springframework.transaction.annotation.Transactional;

class CampaignContractClosureIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenants;
    @Autowired TenantLocaleRepository tenantLocales;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CustomerService customers;
    @Autowired CampaignService campaigns;
    @Autowired CampaignFrontendQueryService campaignQueries;
    @Autowired CampaignPreviewService campaignPreview;
    @Autowired CampaignRepository campaignRepository;
    @Autowired ObjectMapper json;

    @Test
    @Transactional
    void duplicatePrepareCommandReturnsSameRun() {
        Fixture fixture = fixture();

        var campaign =
                campaigns.create(
                        fixture.tenantId(),
                        "Idempotent campaign",
                        fixture.templateVersionId(),
                        "EMAIL",
                        null,
                        CampaignSelection.customer(Set.of(fixture.customerId()), Set.of()),
                        null);
        campaigns.activate(fixture.tenantId(), campaign.getId());

        UUID commandId = UUID.randomUUID();
        var first = campaigns.prepare(fixture.tenantId(), campaign.getId(), commandId);
        var second = campaigns.prepare(fixture.tenantId(), campaign.getId(), commandId);

        assertThat(second.runId()).isEqualTo(first.runId());
        assertThat(second.recipients()).isEqualTo(first.recipients());
        assertThat(first.recipients()).isEqualTo(1);
    }

    @Test
    @Transactional
    void staleDraftRevisionIsRejected() {
        Fixture fixture = fixture();

        var campaign =
                campaigns.create(
                        fixture.tenantId(),
                        "Revision campaign",
                        fixture.templateVersionId(),
                        "EMAIL",
                        null,
                        CampaignSelection.customer(Set.of(fixture.customerId()), Set.of()),
                        null);
        long originalRevision = campaign.getVersion();

        campaigns.updateDraft(
                fixture.tenantId(),
                campaign.getId(),
                "Updated campaign",
                fixture.templateVersionId(),
                "EMAIL",
                null,
                CampaignSelection.customer(Set.of(fixture.customerId()), Set.of()),
                null,
                false,
                originalRevision);
        campaignRepository.flush();

        assertThatThrownBy(
                        () ->
                                campaigns.updateDraft(
                                        fixture.tenantId(),
                                        campaign.getId(),
                                        "Stale update",
                                        fixture.templateVersionId(),
                                        "EMAIL",
                                        null,
                                        CampaignSelection.customer(
                                                Set.of(fixture.customerId()), Set.of()),
                                        null,
                                        false,
                                        originalRevision))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("code")
                .isEqualTo("VERSION_CONFLICT");
    }

    @Test
    @Transactional
    void previewValidationAndDirectRunAreEndToEndConsistent() {
        Fixture fixture = fixture();

        var campaign =
                campaigns.create(
                        fixture.tenantId(),
                        "End-to-end campaign",
                        fixture.templateVersionId(),
                        "EMAIL",
                        null,
                        CampaignSelection.customer(Set.of(fixture.customerId()), Set.of()),
                        null);

        var validation = campaigns.validate(fixture.tenantId(), campaign.getId());
        assertThat(validation.valid()).isTrue();
        assertThat(validation.errors()).isEmpty();

        var preview = campaignPreview.preview(fixture.tenantId(), campaign.getId(), null, null);
        assertThat(preview.customerId()).isEqualTo(fixture.customerId());
        assertThat(preview.channel().name()).isEqualTo("EMAIL");
        assertThat(preview.destination()).contains("***");
        assertThat(preview.body()).contains("Customer");

        campaigns.activate(fixture.tenantId(), campaign.getId(), campaign.getVersion());
        UUID commandId = UUID.randomUUID();
        var prepared = campaigns.prepare(fixture.tenantId(), campaign.getId(), commandId);
        var run = campaignQueries.run(fixture.tenantId(), campaign.getId(), prepared.runId());

        assertThat(run.campaignId()).isEqualTo(campaign.getId());
        assertThat(run.recipientCount()).isEqualTo(1);
        assertThat(run.pendingCount()).isEqualTo(1);
    }

    private Fixture fixture() {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("campaign-f3-" + UUID.randomUUID(), "Campaign F3 Integration"));
        tenantLocales.saveAndFlush(new TenantLocale(tenant.getId(), "ru", true, true, 0));

        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "F3_" + UUID.randomUUID().toString().replace("-", ""),
                                "F3 notification",
                                "NOTIFICATION"));
        TemplateVersion version =
                new TemplateVersion(
                        template.getId(),
                        1,
                        "ru",
                        TemplateChannel.EMAIL,
                        "Notification",
                        "<p>Hello {{customer.name}}</p>",
                        null);
        version.validated();
        version.publish();
        version = versions.saveAndFlush(version);

        var customer =
                customers.create(
                        tenant.getId(),
                        "customer-" + UUID.randomUUID(),
                        CustomerType.INDIVIDUAL,
                        "Customer",
                        "Customer",
                        null,
                        null,
                        null,
                        null,
                        "ru",
                        "Asia/Almaty",
                        json.createObjectNode());
        customers.addEmail(tenant.getId(), customer.getId(), "f3@example.com", "WORK", true);

        return new Fixture(tenant.getId(), version.getId(), customer.getId());
    }

    private record Fixture(UUID tenantId, UUID templateVersionId, UUID customerId) {}
}
