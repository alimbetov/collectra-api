package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRecipientRepository;
import io.collectra.api.campaign.infrastructure.CampaignRepository;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageDeliveryAttempt;
import io.collectra.api.communication.infrastructure.MessageDeliveryAttemptRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "collectra.security.platform-bootstrap.enabled=true",
            "collectra.security.platform-bootstrap.email=reporting-super-admin",
            "collectra.security.platform-bootstrap.password=Reporting_Admin_123!"
        })
class CommunicationAnalyticsIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired TenantRepository tenants;
    @Autowired UserAccountRepository users;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired CampaignRepository campaigns;
    @Autowired CampaignRunRepository runs;
    @Autowired CampaignRecipientRepository recipients;
    @Autowired CustomerService customers;
    @Autowired MessageRepository messages;
    @Autowired MessageDeliveryAttemptRepository attempts;
    @Autowired CommunicationAnalyticsQueryService analytics;

    @Test
    void tenantAndPlatformUseSameMetricDefinitionsWithStrictTenantScope() throws Exception {
        Fixture first = fixture("r1-one", false);
        Fixture second = fixture("r1-two", true);

        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

        JsonNode tenantSummary =
                read(
                        get("/api/v1/analytics/communication/summary")
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .queryParam("tenantId", second.tenantId().toString())
                                .header("Authorization", bearer(first.accessToken())));

        assertThat(tenantSummary.at("/business/recipients").asLong()).isEqualTo(4);
        assertThat(tenantSummary.at("/business/sent").asLong()).isEqualTo(1);
        assertThat(tenantSummary.at("/business/failed").asLong()).isEqualTo(1);
        assertThat(tenantSummary.at("/business/skipped").asLong()).isEqualTo(1);
        assertThat(tenantSummary.at("/business/retries").asLong()).isEqualTo(1);
        assertThat(tenantSummary.at("/messages/queued").asLong()).isEqualTo(1);
        assertThat(tenantSummary.at("/messages/retryWait").asLong()).isEqualTo(1);
        assertThat(tenantSummary.at("/messages/unknown").asLong()).isEqualTo(1);
        assertThat(tenantSummary.get("terminalSuccessRate").decimalValue())
                .isEqualByComparingTo("0.500000");

        String platform = platformToken();
        JsonNode platformFirst =
                read(
                        get("/api/v1/platform/analytics/communication/summary")
                                .queryParam("tenantId", first.tenantId().toString())
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .header("Authorization", bearer(platform)));

        assertThat(platformFirst.get("business")).isEqualTo(tenantSummary.get("business"));
        assertThat(platformFirst.get("messages")).isEqualTo(tenantSummary.get("messages"));

        JsonNode platformSecond =
                read(
                        get("/api/v1/platform/analytics/communication/summary")
                                .queryParam("tenantId", second.tenantId().toString())
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .header("Authorization", bearer(platform)));
        assertThat(platformSecond.at("/business/recipients").asLong()).isEqualTo(4);

        mockMvc.perform(
                        get("/api/v1/platform/analytics/communication/summary")
                                .header("Authorization", bearer(first.accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesUserSystemChannelFailureAndPaginationReports() throws Exception {
        Fixture fixture = fixture("r1-breakdown", true);
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

        JsonNode userPage =
                read(
                        get("/api/v1/analytics/communication/users")
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .queryParam("page", "0")
                                .queryParam("size", "50")
                                .header("Authorization", bearer(fixture.accessToken())));
        assertThat(userPage.get("items").isArray()).isTrue();
        assertThat(userPage.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(userPage.findValuesAsText("email")).contains(fixture.email());

        JsonNode channels =
                read(
                        get("/api/v1/analytics/communication/channels")
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .header("Authorization", bearer(fixture.accessToken())));
        assertThat(channels.findValuesAsText("channel")).contains("EMAIL");
        assertThat(channels.at("/items/0/unknownCurrent").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode failures =
                read(
                        get("/api/v1/analytics/communication/failures")
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .header("Authorization", bearer(fixture.accessToken())));
        assertThat(failures.findValuesAsText("errorCode")).contains("SMTP_550", "TIMEOUT");

        JsonNode campaignsPage =
                read(
                        get("/api/v1/analytics/communication/campaigns")
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .queryParam("sort", "sent,desc")
                                .header("Authorization", bearer(fixture.accessToken())));
        assertThat(campaignsPage.get("items").size()).isGreaterThanOrEqualTo(1);
        assertThat(campaignsPage.at("/items/0/tenantId").asText())
                .isEqualTo(fixture.tenantId().toString());

        JsonNode timeseries =
                read(
                        get("/api/v1/analytics/communication/timeseries")
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .queryParam("bucket", "HOUR")
                                .header("Authorization", bearer(fixture.accessToken())));
        assertThat(timeseries.get("generatedAt").asText()).isNotBlank();
        assertThat(timeseries.get("items").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void rejectsOversizedReportRangeAndUnsupportedSort() throws Exception {
        Fixture fixture = fixture("r1-bounds", false);

        mockMvc.perform(
                        get("/api/v1/analytics/communication/summary")
                                .queryParam("from", "2026-01-01T00:00:00Z")
                                .queryParam("to", "2026-06-01T00:00:00Z")
                                .header("Authorization", bearer(fixture.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_RANGE_TOO_LARGE"));

        mockMvc.perform(
                        get("/api/v1/analytics/communication/campaigns")
                                .queryParam("sort", "destination,asc")
                                .header("Authorization", bearer(fixture.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_SORT"));
    }

    @Test
    void dashboardDeliveryUsesSameAuthoritativeCampaignRunCounters() {
        Fixture fixture = fixture("r1-dashboard", false);

        var totals = analytics.deliveryTotalsAllTime(fixture.tenantId());

        assertThat(totals.recipients()).isEqualTo(4);
        assertThat(totals.sent()).isEqualTo(1);
        assertThat(totals.failed()).isEqualTo(1);
        assertThat(totals.skipped()).isEqualTo(1);
        assertThat(totals.retries()).isEqualTo(1);
    }

    private Fixture fixture(String prefix, boolean createdByUser) {
        try {
            String slug = prefix + "-" + UUID.randomUUID();
            String email = UUID.randomUUID() + "@example.test";
            JsonNode registration =
                    read(
                            post("/api/v1/auth/tenants/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {
                                              "slug": "%s",
                                              "companyName": "Reporting tenant",
                                              "email": "%s",
                                              "password": "StrongPassword123!"
                                            }
                                            """
                                                    .formatted(slug, email)));
            String token = registration.get("accessToken").asText();
            var tenant = tenants.findBySlugIgnoreCase(slug).orElseThrow();
            var user = users.findByTenantIdAndEmailIgnoreCase(tenant.getId(), email).orElseThrow();

            DocumentTemplate template =
                    templates.saveAndFlush(
                            new DocumentTemplate(
                                    tenant.getId(),
                                    "REPORT_" + UUID.randomUUID(),
                                    "Reporting template",
                                    "NOTIFICATION"));
            TemplateVersion version =
                    new TemplateVersion(
                            template.getId(),
                            1,
                            "ru",
                            TemplateChannel.EMAIL,
                            "Subject",
                            "<p>Hello</p>",
                            null);
            version.validated();
            version.publish();
            version = versions.saveAndFlush(version);

            UUID owner = createdByUser ? user.getId() : null;
            Campaign campaign =
                    campaigns.saveAndFlush(
                            new Campaign(
                                    tenant.getId(),
                                    "Reporting campaign",
                                    version.getId(),
                                    "EMAIL",
                                    null,
                                    json.createObjectNode(),
                                    owner));

            Instant now = Instant.now();
            CampaignRun run = new CampaignRun(tenant.getId(), campaign.getId());
            run.ready(4, now);
            run.start(now);
            run.messageSent();
            run.messageFailed();
            run.recipientSkipped();
            run.messageRetryScheduled();
            run = runs.saveAndFlush(run);

            var customer =
                    customers.create(
                            tenant.getId(),
                            "reporting-" + UUID.randomUUID(),
                            CustomerType.INDIVIDUAL,
                            "Reporting Customer",
                            "Reporting",
                            "Customer",
                            null,
                            null,
                            null,
                            "ru",
                            "UTC",
                            json.createObjectNode());

            Message queued =
                    message(
                            tenant.getId(),
                            campaign.getId(),
                            run.getId(),
                            customer.getId(),
                            version.getId(),
                            "queued@example.test");
            messages.saveAndFlush(queued);

            Message retry =
                    message(
                            tenant.getId(),
                            campaign.getId(),
                            run.getId(),
                            customer.getId(),
                            version.getId(),
                            "retry@example.test");
            retry.beginAttempt(now);
            retry.beginProviderAttempt();
            retry.scheduleRetry(now.plusSeconds(60), "TIMEOUT", "timeout");
            retry = messages.saveAndFlush(retry);
            MessageDeliveryAttempt retryAttempt =
                    MessageDeliveryAttempt.started(
                            tenant.getId(), retry.getId(), 1, retry.getDeliveryKey(), now);
            retryAttempt.retryableFailure("TIMEOUT", now.plusSeconds(1));
            attempts.saveAndFlush(retryAttempt);

            Message unknown =
                    message(
                            tenant.getId(),
                            campaign.getId(),
                            run.getId(),
                            customer.getId(),
                            version.getId(),
                            "unknown@example.test");
            unknown.beginAttempt(now);
            unknown.beginProviderAttempt();
            unknown.markUnknown("SMTP_550", "unknown result");
            unknown = messages.saveAndFlush(unknown);
            MessageDeliveryAttempt unknownAttempt =
                    MessageDeliveryAttempt.started(
                            tenant.getId(), unknown.getId(), 1, unknown.getDeliveryKey(), now);
            unknownAttempt.unknown("SMTP_550", now.plusSeconds(1));
            attempts.saveAndFlush(unknownAttempt);

            return new Fixture(
                    tenant.getId(), user.getId(), email, token, campaign.getId(), run.getId());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Message message(
            UUID tenantId,
            UUID campaignId,
            UUID runId,
            UUID customerId,
            UUID templateVersionId,
            String destination) {
        CampaignRecipient recipient =
                recipients.saveAndFlush(
                        new CampaignRecipient(
                                tenantId,
                                campaignId,
                                runId,
                                customerId,
                                null,
                                CommunicationChannel.EMAIL.name(),
                                destination,
                                "ru"));
        return Message.queued(
                tenantId,
                campaignId,
                runId,
                recipient.getId(),
                customerId,
                null,
                templateVersionId,
                CommunicationChannel.EMAIL,
                destination,
                "ru",
                "Subject",
                "<p>Hello</p>");
    }

    private String platformToken() throws Exception {
        JsonNode response =
                read(
                        post("/api/v1/platform/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email":"reporting-super-admin",
                                          "password":"Reporting_Admin_123!"
                                        }
                                        """));
        return response.get("accessToken").asText();
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body =
                mockMvc.perform(request)
                        .andExpect(status().is2xxSuccessful())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Fixture(
            UUID tenantId,
            UUID userId,
            String email,
            String accessToken,
            UUID campaignId,
            UUID runId) {}
}
