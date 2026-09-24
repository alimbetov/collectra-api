package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignMessageMaterializer;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.communication.application.MessageDocumentAccessRecorder;
import io.collectra.api.communication.application.MessageDocumentLinkService;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.MessageDocumentLinkStatus;
import io.collectra.api.communication.infrastructure.MessageDocumentLinkRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.document.application.GenerationJobService;
import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService;
import io.collectra.api.localization.infrastructure.TenantLocaleRepository;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class DocumentLinkCampaignIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenants;
    @Autowired TenantLocaleRepository tenantLocales;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CustomerService customers;
    @Autowired CampaignService campaigns;
    @Autowired CampaignMessageMaterializer materializer;
    @Autowired MessageRepository messages;
    @Autowired MessageDocumentLinkRepository documentLinks;
    @Autowired MessageDocumentLinkService documentLinkService;
    @Autowired MessageDocumentAccessRecorder accessRecorder;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired GenerationJobService generationJobs;
    @Autowired GeneratedDocumentRepository generatedDocuments;
    @Autowired ObjectMapper json;
    @Autowired CommunicationAnalyticsQueryService analytics;

    @Test
    void messageUsesSeparatePdfTemplateAndWaitsForSecureLinkReadiness() throws Exception {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("document-link-" + UUID.randomUUID(), "Document Link Test"));
        tenantLocales.saveAndFlush(new TenantLocale(tenant.getId(), "ru", true, true, 0));

        DocumentTemplate messageTemplate =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "EMAIL_NOTICE_" + UUID.randomUUID(),
                                "Email notice",
                                "NOTIFICATION"));
        TemplateVersion messageVersion =
                new TemplateVersion(
                        messageTemplate.getId(),
                        1,
                        "ru",
                        TemplateChannel.EMAIL,
                        "Document for {{customer.name}}",
                        "<p>Hello {{customer.name}}</p><a href=\"{{document.url}}\">PDF</a>",
                        null);
        messageVersion.validated();
        messageVersion.publish();
        messageVersion = versions.saveAndFlush(messageVersion);

        DocumentTemplate pdfTemplate =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "PDF_NOTICE_" + UUID.randomUUID(),
                                "PDF notice",
                                "NOTIFICATION"));
        TemplateVersion pdfVersion =
                new TemplateVersion(
                        pdfTemplate.getId(),
                        1,
                        "ru",
                        TemplateChannel.PDF,
                        null,
                        "<p>PDF for {{customer.name}}</p>",
                        null);
        pdfVersion.validated();
        pdfVersion.publish();
        pdfVersion = versions.saveAndFlush(pdfVersion);

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
        customers.addEmail(
                tenant.getId(), customer.getId(), "document-link@example.com", "WORK", true);

        var campaign =
                campaigns.create(
                        tenant.getId(),
                        "Document link campaign",
                        messageVersion.getId(),
                        CommunicationChannel.EMAIL.name(),
                        null,
                        CampaignSelection.customer(Set.of(customer.getId()), Set.of()),
                        pdfVersion.getId(),
                        true,
                        null);
        campaigns.activate(tenant.getId(), campaign.getId());
        var prepared = campaigns.prepare(tenant.getId(), campaign.getId());

        var materialized = materializer.materializeNextBatch(tenant.getId(), prepared.runId(), 100);

        assertThat(materialized.queued()).isEqualTo(1);
        var message =
                messages.findAllByTenantIdAndCampaignRunId(
                                tenant.getId(), prepared.runId(), PageRequest.of(0, 10))
                        .getContent()
                        .get(0);

        assertThat(message.getTemplateVersionId()).isEqualTo(messageVersion.getId());
        assertThat(message.getBody()).contains("Hello Test Customer");
        assertThat(message.getBody()).contains("/api/v1/public/documents/");
        assertThat(message.getDeliveryRequestedAt()).isNull();

        var link =
                documentLinks
                        .findByTenantIdAndMessageId(tenant.getId(), message.getId())
                        .orElseThrow();
        assertThat(link.getStatus()).isEqualTo(MessageDocumentLinkStatus.PENDING);

        var job = generationJobs.get(tenant.getId(), link.getGenerationJobId());
        assertThat(job.getTemplateVersionId()).isEqualTo(pdfVersion.getId());

        String token = tokenFrom(message.getBody());
        assertThat(documentLinks.findByTokenHash(sha256(token))).isPresent();

        generatedDocuments.saveAndFlush(
                new GeneratedDocument(
                        tenant.getId(),
                        job.getId(),
                        OutputFormat.PDF,
                        tenant.getId() + "/test/document.pdf",
                        "application/pdf",
                        3,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        Instant.parse("2026-09-22T00:00:00Z")));

        assertThat(documentLinkService.generationCompleted(tenant.getId(), job.getId())).isTrue();

        var reloadedMessage = messages.findById(message.getId()).orElseThrow();
        var readyLink =
                documentLinks
                        .findByTenantIdAndMessageId(tenant.getId(), message.getId())
                        .orElseThrow();
        assertThat(readyLink.getStatus()).isEqualTo(MessageDocumentLinkStatus.READY);
        assertThat(reloadedMessage.getDeliveryRequestedAt()).isNotNull();

        Instant firstOpen = Instant.parse("2026-09-24T10:00:00Z");
        Instant secondOpen = firstOpen.plusSeconds(5);
        accessRecorder.record(tenant.getId(), readyLink.getId(), firstOpen);
        accessRecorder.record(tenant.getId(), readyLink.getId(), secondOpen);

        var access =
                jdbc.queryForMap(
                        """
                        select access_count, first_access_at, last_access_at
                          from message_document_links
                         where id = ?
                        """,
                        readyLink.getId());
        assertThat(((Number) access.get("access_count")).longValue()).isEqualTo(2);
        assertThat(((java.sql.Timestamp) access.get("first_access_at")).toInstant())
                .isEqualTo(firstOpen);
        assertThat(((java.sql.Timestamp) access.get("last_access_at")).toInstant())
                .isEqualTo(secondOpen);

        var documentReport =
                analytics.documents(
                        CommunicationAnalyticsQueryService.Scope.tenant(tenant.getId()),
                        new CommunicationAnalyticsQueryService.Filter(
                                Instant.now().minusSeconds(3600),
                                Instant.now().plusSeconds(3600),
                                campaign.getId(),
                                prepared.runId(),
                                "EMAIL",
                                null));
        assertThat(documentReport.totals().accessCount()).isEqualTo(2);
        assertThat(documentReport.totals().accessedLinks()).isEqualTo(1);
    }

    private String tokenFrom(String body) {
        String marker = "/api/v1/public/documents/";
        int start = body.indexOf(marker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        start += marker.length();
        int end = body.indexOf('"', start);
        return end < 0 ? body.substring(start) : body.substring(start, end);
    }

    private String sha256(String value) throws Exception {
        byte[] digest =
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
