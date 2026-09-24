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
import io.collectra.api.identity.application.PlatformAdministratorService;
import io.collectra.api.identity.application.PlatformAuthService;
import io.collectra.api.identity.application.RbacService;
import io.collectra.api.identity.domain.SystemRole;
import io.collectra.api.identity.domain.TenantMembership;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.platform.application.PlatformUserLifecycleService;
import io.collectra.api.shared.error.BusinessConflictException;
import io.collectra.api.shared.error.InvalidRefreshTokenException;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PlatformUserAdministrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired AuthService auth;
    @Autowired PlatformAdministratorService platformAdministrators;
    @Autowired PlatformAuthService platformAuth;
    @Autowired PlatformUserLifecycleService lifecycle;
    @Autowired TenantRepository tenants;
    @Autowired UserAccountRepository users;
    @Autowired TenantMembershipRepository memberships;
    @Autowired RbacService rbac;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void userRegistryIsPagedFilteredAndPlatformOnly() throws Exception {
        TestUser user = createTenantUser();

        JsonNode response =
                read(
                        get("/api/v1/platform/users")
                                .queryParam("search", user.email())
                                .queryParam("tenantId", user.tenantId().toString())
                                .queryParam("roleCode", "TENANT_USER")
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .header("Authorization", "Bearer " + platformAccessToken()));

        assertThat(response.get("items").size()).isEqualTo(1);
        assertThat(response.at("/items/0/userId").asText()).isEqualTo(user.userId().toString());
        assertThat(response.at("/items/0/membershipId").asText())
                .isEqualTo(user.membershipId().toString());
        assertThat(response.at("/items/0/accountStatus").asText()).isEqualTo("ACTIVE");
        assertThat(response.at("/items/0/membershipStatus").asText()).isEqualTo("ACTIVE");
        assertThat(response.at("/items/0/effectiveAccessStatus").asText()).isEqualTo("ACTIVE");
        assertThat(response.at("/items/0/roleCodes").findValuesAsText(""))
                .doesNotContain("tokenHash");

        var tenantTokens = auth.login(user.tenantSlug(), user.email(), user.password());
        mockMvc.perform(
                        get("/api/v1/platform/users")
                                .header("Authorization", "Bearer " + tenantTokens.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockMembershipInvalidatesAccessRefreshAndWritesAudit() throws Exception {
        TestUser user = createTenantUser();
        AuthService.AuthTokens tokens = auth.login(user.tenantSlug(), user.email(), user.password());

        JsonNode detail =
                read(
                        get("/api/v1/platform/users/{userId}", user.userId())
                                .header("Authorization", "Bearer " + platformAccessToken()));
        long revision = detail.get("revision").asLong();

        JsonNode blocked =
                read(
                        patch(
                                        "/api/v1/platform/memberships/{membershipId}/status",
                                        user.membershipId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "active": false,
                                          "revision": %d,
                                          "reason": "Security investigation"
                                        }
                                        """
                                                .formatted(revision))
                                .header("Authorization", "Bearer " + platformAccessToken()));

        assertThat(blocked.get("membershipStatus").asText()).isEqualTo("BLOCKED");
        assertThat(blocked.get("effectiveAccessStatus").asText()).isEqualTo("BLOCKED");

        mockMvc.perform(
                        get("/api/v1/identity/me")
                                .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        Integer activeRefresh =
                jdbc.queryForObject(
                        """
                        select count(*)
                          from refresh_sessions
                         where membership_id = ?
                           and revoked_at is null
                        """,
                        Integer.class,
                        user.membershipId());
        assertThat(activeRefresh).isZero();

        Integer auditCount =
                jdbc.queryForObject(
                        """
                        select count(*)
                          from security_audit_events
                         where tenant_id = ?
                           and action = 'TENANT_MEMBERSHIP_BLOCKED'
                           and result = 'SUCCEEDED'
                        """,
                        Integer.class,
                        user.tenantId());
        assertThat(auditCount).isPositive();
    }

    @Test
    void staleMembershipRevisionReturnsStableConflict() throws Exception {
        TestUser user = createTenantUser();

        mockMvc.perform(
                        patch(
                                        "/api/v1/platform/memberships/{membershipId}/status",
                                        user.membershipId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "active": false,
                                          "revision": 9999,
                                          "reason": "stale command"
                                        }
                                        """)
                                .header("Authorization", "Bearer " + platformAccessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        assertThat(memberships.findById(user.membershipId()).orElseThrow().active()).isTrue();
    }

    @Test
    void lastActiveTenantAdminCannotBeBlocked() {
        String slug = "pf3-admin-" + UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.test";
        auth.register(slug, "PF3 Admin Tenant", email, "StrongPassword123!");
        var tenant = tenants.findBySlugIgnoreCase(slug).orElseThrow();
        var admin = users.findByTenantIdAndEmailIgnoreCase(tenant.getId(), email).orElseThrow();
        var membership =
                memberships.findByTenantIdAndUserId(tenant.getId(), admin.getId()).orElseThrow();

        assertThatThrownBy(
                        () ->
                                lifecycle.changeMembershipStatus(
                                        UUID.randomUUID(),
                                        membership.getId(),
                                        false,
                                        membership.getVersion(),
                                        "attempt to block last admin"))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(
                        error ->
                                assertThat(((BusinessConflictException) error).getCode())
                                        .isEqualTo("LAST_ACTIVE_TENANT_ADMIN"));

        assertThat(memberships.findById(membership.getId()).orElseThrow().active()).isTrue();
    }

    @Test
    void sessionRegistryIsBoundedAndDoesNotExposeTokenMaterial() throws Exception {
        TestUser user = createTenantUser();
        auth.login(user.tenantSlug(), user.email(), user.password());

        JsonNode response =
                read(
                        get(
                                        "/api/v1/platform/memberships/{membershipId}/sessions",
                                        user.membershipId())
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .header("Authorization", "Bearer " + platformAccessToken()));

        assertThat(response.get("items").size()).isEqualTo(1);
        assertThat(response.toString()).doesNotContain("tokenHash");
        assertThat(response.toString()).doesNotContain("refreshToken");
        assertThat(response.at("/items/0/sourceIp").isMissingNode()).isFalse();
    }

    private TestUser createTenantUser() {
        String slug = "pf3-" + UUID.randomUUID();
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.test";
        auth.register(slug, "PF3 Tenant", adminEmail, "StrongPassword123!");
        var tenant = tenants.findBySlugIgnoreCase(slug).orElseThrow();

        String email = "user-" + UUID.randomUUID() + "@example.test";
        String password = "StrongUserPassword123!";
        UserAccount user =
                users.saveAndFlush(
                        new UserAccount(
                                tenant.getId(),
                                email,
                                passwordEncoder.encode(password),
                                SystemRole.TENANT_USER));
        TenantMembership membership =
                memberships.saveAndFlush(new TenantMembership(tenant.getId(), user.getId()));
        rbac.assignSystemRole(membership.getId(), RbacService.TENANT_USER_ROLE);

        return new TestUser(
                tenant.getId(), slug, user.getId(), membership.getId(), email, password);
    }

    private String platformAccessToken() {
        String email = "pf3-platform-" + UUID.randomUUID() + "@example.test";
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

    private record TestUser(
            UUID tenantId,
            String tenantSlug,
            UUID userId,
            UUID membershipId,
            String email,
            String password) {}
}
