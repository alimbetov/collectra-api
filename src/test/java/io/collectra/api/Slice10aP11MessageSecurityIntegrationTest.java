package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class Slice10aP11MessageSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired DocumentTemplateRepository documentTemplates;
    @Autowired TemplateVersionRepository templateVersions;
    @Autowired CustomerService customers;
    @Autowired ReceivableService receivables;
    @Autowired CampaignService campaigns;
    @Autowired CampaignRunRepository runs;
    @Autowired MessageRepository messages;

    @Test
    void messageEndpointsRejectAnonymousInvalidAndExpiredAuthentication() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String detailPath = messagePath(campaignId, runId) + "/" + messageId;

        mockMvc.perform(get(detailPath)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(detailPath).header("Authorization", "Bearer definitely-not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(detailPath).header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantCannotDiscoverForeignMessageOrCampaignRun() throws Exception {
        Auth alpha = registerTenant("p11-alpha");
        Auth beta = registerTenant("p11-beta");
        Fixture betaFixture = fixture(beta.tenantId());

        mockMvc.perform(
                        get(messagePath(betaFixture.campaignId(), betaFixture.runId())
                                        + "/"
                                        + betaFixture.messageId())
                                .header("Authorization", bearer(alpha.accessToken())))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        get(messagePath(betaFixture.campaignId(), betaFixture.runId()))
                                .header("Authorization", bearer(alpha.accessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerTenantSeesMaskedDestinationAndNeverRawEmail() throws Exception {
        Auth owner = registerTenant("p11-owner");
        Fixture fixture = fixture(owner.tenantId());

        String response =
                mockMvc.perform(
                                get(messagePath(fixture.campaignId(), fixture.runId())
                                                + "/"
                                                + fixture.messageId())
                                        .header("Authorization", bearer(owner.accessToken())))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(fixture.messageId().toString()))
                        .andExpect(jsonPath("$.maskedDestination").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(response).doesNotContain(fixture.rawDestination());
    }

    @Test
    void excessiveMessagePageSizeIsRejectedBeforeQueryAmplification() throws Exception {
        Auth owner = registerTenant("p11-page");
        Fixture fixture = fixture(owner.tenantId());

        mockMvc.perform(
                        get(messagePath(fixture.campaignId(), fixture.runId()))
                                .queryParam("size", "201")
                                .header("Authorization", bearer(owner.accessToken())))
                .andExpect(status().isBadRequest());
    }

    private Auth registerTenant(String prefix) throws Exception {
        String slug = prefix + "-" + UUID.randomUUID();
        String email = UUID.randomUUID() + "@test.invalid";
        String body =
                "{\"slug\":\""
                        + slug
                        + "\",\"companyName\":\"P11 Security\",\"email\":\""
                        + email
                        + "\",\"password\":\"StrongPassword123!\"}";
        String response =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                                "/api/v1/auth/tenants/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode payload = json.readTree(response);
        String accessToken = payload.get("accessToken").asText();
        UUID tenantId = UUID.fromString(jwtDecoder.decode(accessToken).getClaimAsString("tenant_id"));
        return new Auth(tenantId, accessToken);
    }

    private Fixture fixture(UUID tenantId) {
        String suffix = UUID.randomUUID().toString();
        DocumentTemplate documentTemplate =
                documentTemplates.saveAndFlush(
                        new DocumentTemplate(
                                tenantId,
                                "P11_REMINDER_" + suffix,
                                "P11 payment reminder",
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
                        tenantId,
                        "customer-" + suffix,
                        CustomerType.INDIVIDUAL,
                        "P11 Customer",
                        "P11",
                        "Customer",
                        null,
                        null,
                        null,
                        "ru-KZ",
                        "Asia/Almaty",
                        json.createObjectNode());
        String rawDestination = "secret-" + suffix + "@test.invalid";
        customers.addEmail(tenantId, customer.getId(), rawDestination, "WORK", true);

        LocalDate today = LocalDate.now();
        Invoice invoice =
                receivables.createInvoice(
                        tenantId,
                        customer.getId(),
                        null,
                        "invoice-" + suffix,
                        "P11-INV-" + suffix,
                        today.minusDays(20),
                        today.minusDays(10),
                        new BigDecimal("10000.00"),
                        "KZT",
                        null,
                        json.createObjectNode());

        var campaign =
                campaigns.create(
                        tenantId,
                        "P11 tenant isolation",
                        templateVersion.getId(),
                        "EMAIL",
                        null,
                        new CampaignSelection(Set.of(), Set.of(), 1, 30, null, null),
                        null);
        campaigns.activate(tenantId, campaign.getId());
        var prepared = campaigns.prepare(tenantId, campaign.getId());
        CampaignRun run = campaigns.run(tenantId, prepared.runId());
        run.start(Instant.now());
        run = runs.saveAndFlush(run);
        CampaignRecipient recipient = campaigns.recipients(tenantId, prepared.runId()).get(0);

        Message message =
                messages.saveAndFlush(
                        Message.queued(
                                tenantId,
                                campaign.getId(),
                                run.getId(),
                                recipient.getId(),
                                customer.getId(),
                                invoice.getId(),
                                templateVersion.getId(),
                                CommunicationChannel.EMAIL,
                                rawDestination,
                                templateVersion.getLocale(),
                                templateVersion.getSubject(),
                                templateVersion.getContentHtml()));

        return new Fixture(
                campaign.getId(), run.getId(), message.getId(), customer.getId(), rawDestination);
    }

    private String expiredToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer("collectra-api")
                        .audience(List.of("collectra-api"))
                        .subject(UUID.randomUUID().toString())
                        .issuedAt(now.minusSeconds(120))
                        .expiresAt(now.minusSeconds(60))
                        .claim("tenant_id", UUID.randomUUID().toString())
                        .claim("membership_id", UUID.randomUUID().toString())
                        .claim("roles", List.of("TENANT_ADMIN"))
                        .claim("permissions", List.of("CAMPAIGN_READ"))
                        .claim("authorization_version", 0L)
                        .claim("token_type", "user")
                        .build();
        return jwtEncoder
                .encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String messagePath(UUID campaignId, UUID runId) {
        return "/api/v1/campaigns/" + campaignId + "/runs/" + runId + "/messages";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Auth(UUID tenantId, String accessToken) {}

    private record Fixture(
            UUID campaignId,
            UUID runId,
            UUID messageId,
            UUID customerId,
            String rawDestination) {}
}
