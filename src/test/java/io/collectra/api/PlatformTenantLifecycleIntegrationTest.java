package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.identity.application.AuthService;
import io.collectra.api.identity.application.AuthService.AuthTokens;
import io.collectra.api.integration.application.ServiceClientService;
import io.collectra.api.platform.application.PlatformTenantLifecycleService;
import io.collectra.api.shared.error.InvalidRefreshTokenException;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class PlatformTenantLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired AuthService auth;
    @Autowired ServiceClientService serviceClients;
    @Autowired PlatformTenantLifecycleService lifecycle;
    @Autowired TenantRepository tenants;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void blockRevokesAccessAndActivationDoesNotRestoreOldRefreshToken() {
        String slug = "pf2-" + UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.test";
        String password = "StrongPassword123!";
        AuthTokens registered = auth.register(slug, "PF2 Company", email, password);
        var tenant = tenants.findBySlugIgnoreCase(slug).orElseThrow();

        String clientId = "pf2-" + UUID.randomUUID().toString().substring(0, 8);
        String clientSecret = "pf2-service-secret-12345678901234567890";
        serviceClients.create(
                tenant.getId(),
                clientId,
                "PF2 client",
                clientSecret,
                Set.of("document:read"),
                null,
                null);
        assertThat(serviceClients.token(clientId, clientSecret, Set.of("document:read")))
                .isNotNull();

        UUID actorId = UUID.randomUUID();
        lifecycle.changeStatus(
                actorId, tenant.getId(), false, tenant.getVersion(), "Security test");

        assertThatThrownBy(() -> auth.login(slug, email, password))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> auth.refresh(registered.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(
                        () -> serviceClients.token(clientId, clientSecret, Set.of("document:read")))
                .isInstanceOf(BadCredentialsException.class);

        var blocked = tenants.findById(tenant.getId()).orElseThrow();
        assertThat(blocked.getStatus()).isEqualTo("BLOCKED");
        Integer activeRefreshSessions =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from refresh_sessions
                        where tenant_id = ?
                          and context_type = 'TENANT'
                          and revoked_at is null
                        """,
                        Integer.class,
                        tenant.getId());
        assertThat(activeRefreshSessions).isZero();

        lifecycle.changeStatus(
                actorId, tenant.getId(), true, blocked.getVersion(), "Security issue resolved");

        assertThat(auth.login(slug, email, password)).isNotNull();
        assertThatThrownBy(() -> auth.refresh(registered.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        Integer blockedAudit =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from security_audit_events
                        where tenant_id = ?
                          and actor_id = ?
                          and action = 'TENANT_BLOCKED'
                          and result = 'SUCCEEDED'
                        """,
                        Integer.class,
                        tenant.getId(),
                        actorId);
        Integer activatedAudit =
                jdbc.queryForObject(
                        """
                        select count(*)
                        from security_audit_events
                        where tenant_id = ?
                          and actor_id = ?
                          and action = 'TENANT_ACTIVATED'
                          and result = 'SUCCEEDED'
                        """,
                        Integer.class,
                        tenant.getId(),
                        actorId);
        assertThat(blockedAudit).isEqualTo(1);
        assertThat(activatedAudit).isEqualTo(1);
    }

    @Test
    void platformTenantRegistryIsPagedAndPlatformOnly() throws Exception {
        String marker = "pf2-list-" + UUID.randomUUID();
        tenants.saveAndFlush(
                new io.collectra.api.tenant.domain.Tenant(marker, "PF2 Listed Tenant"));

        JsonNode response =
                read(
                        get("/api/v1/platform/tenants")
                                .queryParam("search", marker)
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.subject(
                                                                                UUID.randomUUID()
                                                                                        .toString())
                                                                        .claim(
                                                                                "token_type",
                                                                                "platform_user")
                                                                        .claim(
                                                                                "authorization_version",
                                                                                0L))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_PLATFORM_SUPER_ADMIN"))));

        assertThat(response.get("items").size()).isEqualTo(1);
        assertThat(response.get("items").get(0).get("slug").asText()).isEqualTo(marker);
        assertThat(response.get("page").asInt()).isZero();
        assertThat(response.get("size").asInt()).isEqualTo(1);

        mockMvc.perform(
                        get("/api/v1/platform/tenants")
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.subject(
                                                                        UUID.randomUUID()
                                                                                .toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_HUMAN"))))
                .andExpect(status().isForbidden());
    }


    @Test
    void lifecyclePatchRejectsStaleRevisionWithStableCode() throws Exception {
        String marker = "pf2-stale-" + UUID.randomUUID();
        var tenant =
                tenants.saveAndFlush(
                        new io.collectra.api.tenant.domain.Tenant(marker, "PF2 Stale Tenant"));

        mockMvc.perform(
                        patch("/api/v1/platform/tenants/{tenantId}/status", tenant.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "active": false,
                                          "revision": 999,
                                          "reason": "stale test"
                                        }
                                        """)
                                .with(platformAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        assertThat(tenants.findById(tenant.getId()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void tenantDetailUsesStableNotFoundCode() throws Exception {
        mockMvc.perform(
                        get("/api/v1/platform/tenants/{tenantId}", UUID.randomUUID())
                                .with(platformAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void tenantListRejectsInvalidFilterAndSort() throws Exception {
        mockMvc.perform(
                        get("/api/v1/platform/tenants")
                                .queryParam("status", "SUSPENDED")
                                .with(platformAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));

        mockMvc.perform(
                        get("/api/v1/platform/tenants")
                                .queryParam("sort", "name,sideways")
                                .with(platformAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_SORT"));
    }

    private RequestPostProcessor platformAdmin() {
        return jwt()
                .jwt(
                        token ->
                                token.subject(UUID.randomUUID().toString())
                                        .claim("token_type", "platform_user")
                                        .claim("authorization_version", 0L))
                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPER_ADMIN"));
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
