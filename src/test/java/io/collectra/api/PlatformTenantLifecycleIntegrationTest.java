package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.identity.application.AuthService;
import io.collectra.api.identity.application.AuthService.AuthTokens;
import io.collectra.api.identity.application.PlatformAdministratorService;
import io.collectra.api.identity.application.PlatformAuthService;
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
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PlatformTenantLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired AuthService auth;
    @Autowired PlatformAdministratorService platformAdministrators;
    @Autowired PlatformAuthService platformAuth;
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
        String clientSecret =
                serviceClients
                        .create(
                                tenant.getId(),
                                clientId,
                                "PF2 client",
                                Set.of("document:read"),
                                null,
                                null)
                        .clientSecret();
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
                                .header("Authorization", "Bearer " + platformAccessToken()));

        assertThat(response.get("items").size()).isEqualTo(1);
        assertThat(response.get("items").get(0).get("slug").asText()).isEqualTo(marker);
        assertThat(response.get("page").asInt()).isZero();
        assertThat(response.get("size").asInt()).isEqualTo(1);

        String tenantSlug = "pf2-human-" + UUID.randomUUID();
        AuthTokens tenantUser =
                auth.register(
                        tenantSlug,
                        "PF2 Human Tenant",
                        UUID.randomUUID() + "@example.test",
                        "StrongPassword123!");
        mockMvc.perform(
                        get("/api/v1/platform/tenants")
                                .header("Authorization", "Bearer " + tenantUser.accessToken()))
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
                                .header("Authorization", "Bearer " + platformAccessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        assertThat(tenants.findById(tenant.getId()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void lifecyclePatchRequiresActiveAndRevision() throws Exception {
        String marker = "pf2-required-" + UUID.randomUUID();
        var tenant =
                tenants.saveAndFlush(
                        new io.collectra.api.tenant.domain.Tenant(marker, "PF2 Required Tenant"));

        mockMvc.perform(
                        patch("/api/v1/platform/tenants/{tenantId}/status", tenant.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "reason": "missing command fields"
                                        }
                                        """)
                                .header("Authorization", "Bearer " + platformAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.active").exists())
                .andExpect(jsonPath("$.errors.revision").exists());
    }

    @Test
    void tenantDetailUsesStableNotFoundCode() throws Exception {
        mockMvc.perform(
                        get("/api/v1/platform/tenants/{tenantId}", UUID.randomUUID())
                                .header("Authorization", "Bearer " + platformAccessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void tenantListRejectsInvalidFilterAndSort() throws Exception {
        mockMvc.perform(
                        get("/api/v1/platform/tenants")
                                .queryParam("status", "SUSPENDED")
                                .header("Authorization", "Bearer " + platformAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));

        mockMvc.perform(
                        get("/api/v1/platform/tenants")
                                .queryParam("sort", "name,sideways")
                                .header("Authorization", "Bearer " + platformAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_SORT"));
    }

    private String platformAccessToken() {
        String email = "pf2-platform-" + UUID.randomUUID() + "@example.test";
        String password = "StrongPlatformPassword123!";
        platformAdministrators.bootstrap(email, password);
        return platformAuth.login(email, password).accessToken();
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
