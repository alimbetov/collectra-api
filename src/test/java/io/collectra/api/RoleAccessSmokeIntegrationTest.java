package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end authorization smoke tests. Tokens are issued through the public authentication APIs,
 * so these tests cover JWT claims, the HTTP security chain, method security and tenant context.
 */
@AutoConfigureMockMvc
class RoleAccessSmokeIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void anonymousActorCanOnlyUsePublicSecurityEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/identity/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/identity/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/identity/roles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/integration/service-clients"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/audit/security-events"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/platform/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tenantAdminCanUseEveryImplementedTenantManagementZone() throws Exception {
        String token = register("admin-smoke").accessToken();

        mockMvc.perform(get("/api/v1/identity/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/identity/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/identity/roles").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/identity/invitations")
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/integration/service-clients")
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/audit/security-events")
                                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/source-schemas").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/mapping-profiles").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/templates").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/platform/me").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantUserHasSelfServiceAndReadOnlyTenantAccess() throws Exception {
        Actor actor = invitedActor(null);

        mockMvc.perform(
                        get("/api/v1/identity/me")
                                .header("Authorization", bearer(actor.accessToken())))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/identity/users")
                                .header("Authorization", bearer(actor.accessToken())))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/identity/roles")
                                .header("Authorization", bearer(actor.accessToken())))
                .andExpect(status().isOk());

        assertTenantManagementForbidden(actor.accessToken());
    }

    @Test
    void customRoleReceivesOnlyItsSelectedPermission() throws Exception {
        Auth admin = register("custom-smoke");
        JsonNode role =
                read(
                        post("/api/v1/identity/roles")
                                .header("Authorization", bearer(admin.accessToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"READER_"
                                                + UUID.randomUUID().toString().replace("-", "")
                                                        .toUpperCase()
                                                + "\",\"permissions\":[\"USER_READ\"]}"));
        Actor actor = invitedActor(role.get("id").asText(), admin);

        mockMvc.perform(
                        get("/api/v1/identity/users")
                                .header("Authorization", bearer(actor.accessToken())))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/identity/roles")
                                .header("Authorization", bearer(actor.accessToken())))
                .andExpect(status().isForbidden());
        assertTenantManagementForbidden(actor.accessToken());
    }

    @Test
    void serviceClientIsRejectedByEveryHumanAndPlatformZone() throws Exception {
        Auth admin = register("service-smoke");
        String secret = "service-client-secret-123456789012345";
        String clientId = "service-" + UUID.randomUUID();
        read(
                post("/api/v1/integration/service-clients")
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"clientId\":\""
                                        + clientId
                                        + "\",\"name\":\"ERP\",\"clientSecret\":\""
                                        + secret
                                        + "\",\"scopes\":[\"integration:imports:read\"]}"));
        JsonNode serviceToken =
                read(
                        post("/api/v1/integration/service-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"clientId\":\""
                                                + clientId
                                                + "\",\"clientSecret\":\""
                                                + secret
                                                + "\",\"scopes\":[\"integration:imports:read\"]}"));
        String token = serviceToken.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/identity/me").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/identity/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/v1/integration/service-clients")
                                .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit/security-events").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/source-schemas").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/mapping-profiles").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/templates").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/platform/me").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    private void assertTenantManagementForbidden(String token) throws Exception {
        mockMvc.perform(
                        get("/api/v1/identity/invitations")
                                .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/v1/integration/service-clients")
                                .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit/security-events").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/source-schemas").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/mapping-profiles").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/templates").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/platform/me").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    private Actor invitedActor(String roleId) throws Exception {
        return invitedActor(roleId, register("user-smoke"));
    }

    private Actor invitedActor(String roleId, Auth admin) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String roleJson = roleId == null ? "" : ",\"roleIds\":[\"" + roleId + "\"]";
        JsonNode invitation =
                read(
                        post("/api/v1/identity/invitations")
                                .header("Authorization", bearer(admin.accessToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\"" + roleJson + "}"));
        JsonNode accepted =
                read(
                        post("/api/v1/auth/invitations/accept")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + invitation.get("developmentToken").asText()
                                                + "\",\"password\":\"UserPassword123!\","
                                                + "\"displayName\":\"Smoke User\"}"));
        return new Actor(accepted.get("accessToken").asText());
    }

    private Auth register(String prefix) throws Exception {
        JsonNode response =
                read(
                        post("/api/v1/auth/tenants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"slug\":\""
                                                + prefix
                                                + "-"
                                                + UUID.randomUUID()
                                                + "\",\"companyName\":\"Smoke Company\","
                                                + "\"email\":\""
                                                + UUID.randomUUID()
                                                + "@example.test\","
                                                + "\"password\":\"StrongPassword123!\"}"));
        return new Auth(response.get("accessToken").asText());
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
        JsonNode result = json.readTree(body);
        assertThat(result).isNotNull();
        return result;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Auth(String accessToken) {}

    private record Actor(String accessToken) {}
}
