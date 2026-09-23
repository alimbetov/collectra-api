package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.platform.application.PlatformOverviewQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "collectra.security.platform-bootstrap.enabled=true",
            "collectra.security.platform-bootstrap.email=super-admin",
            "collectra.security.platform-bootstrap.password=Alimbetov_Ruslan"
        })
class PlatformOverviewIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired UserAccountRepository users;
    @Autowired PlatformOverviewQueryService overviewQueries;

    @Test
    void platformOverviewIsAvailableOnlyToPlatformAdministrator() throws Exception {
        var direct = overviewQueries.overview();
        assertThat(direct.generatedAt()).isNotNull();

        var administrator =
                users.findByTenantIdIsNullAndEmailIgnoreCase("super-admin").orElseThrow();

        JsonNode overview =
                read(
                        get("/api/v1/platform/overview")
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.subject(
                                                                                administrator
                                                                                        .getId()
                                                                                        .toString())
                                                                        .claim(
                                                                                "token_type",
                                                                                "platform_user")
                                                                        .claim(
                                                                                "authorization_version",
                                                                                administrator
                                                                                        .getAuthorizationVersion()))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_PLATFORM_SUPER_ADMIN"))));

        assertThat(overview.get("generatedAt").asText()).isNotBlank();
        assertThat(overview.get("periodFrom").asText()).isNotBlank();
        assertThat(overview.get("tenantsTotal").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(overview.get("usersTotal").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(overview.get("messagesLast30Days").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(overview.get("channels").isArray()).isTrue();

        mockMvc.perform(get("/api/v1/platform/overview")).andExpect(status().isUnauthorized());
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
}
