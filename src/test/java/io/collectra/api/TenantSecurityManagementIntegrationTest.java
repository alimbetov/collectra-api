package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TenantSecurityManagementIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void permissionCatalogAndCustomRoleLifecycleAreEnforced() throws Exception {
        Auth admin = register("role-management");
        JsonNode catalog = read(get("/api/v1/identity/permissions")
                .header("Authorization", bearer(admin.accessToken())));
        assertThat(catalog.findValuesAsText("code")).contains("USER_READ", "ROLE_READ");

        JsonNode role = createRole(admin.accessToken(), "MANAGER_" + randomCode(), "USER_READ");
        User user = invite(admin, role.get("id").asText());
        mockMvc.perform(get("/api/v1/identity/users").header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isOk());

        JsonNode updated = read(put("/api/v1/identity/roles/{id}", role.get("id").asText())
                .header("Authorization", bearer(admin.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"AUDITOR_" + randomCode()
                        + "\",\"permissions\":[\"ROLE_READ\"]}"));
        assertThat(updated.get("permissions").get(0).asText()).isEqualTo("ROLE_READ");
        mockMvc.perform(get("/api/v1/identity/me").header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/identity/roles/{id}", role.get("id").asText())
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isConflict());

        JsonNode unused = createRole(admin.accessToken(), "UNUSED_" + randomCode(), "USER_READ");
        mockMvc.perform(delete("/api/v1/identity/roles/{id}", unused.get("id").asText())
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void tenantAdminListsAndRevokesMembershipSessions() throws Exception {
        Auth admin = register("session-management");
        JsonNode members = read(get("/api/v1/identity/users")
                .header("Authorization", bearer(admin.accessToken())));
        String membershipId = members.get(0).get("id").asText();
        JsonNode sessions = read(get("/api/v1/identity/memberships/{id}/sessions", membershipId)
                .header("Authorization", bearer(admin.accessToken())));
        assertThat(sessions).hasSize(1);

        mockMvc.perform(delete("/api/v1/identity/memberships/{id}/sessions/{sessionId}",
                                membershipId, sessions.get(0).get("id").asText())
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Token(admin.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode createRole(String token, String code, String permission) throws Exception {
        return read(post("/api/v1/identity/roles")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"permissions\":[\"" + permission + "\"]}"));
    }

    private User invite(Auth admin, String roleId) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        JsonNode invitation = read(post("/api/v1/identity/invitations")
                .header("Authorization", bearer(admin.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"roleIds\":[\"" + roleId + "\"]}"));
        JsonNode accepted = read(post("/api/v1/auth/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + invitation.get("developmentToken").asText()
                        + "\",\"password\":\"UserPassword123!\",\"displayName\":\"User\"}"));
        return new User(email, accepted.get("accessToken").asText());
    }

    private Auth register(String prefix) throws Exception {
        JsonNode response = read(post("/api/v1/auth/tenants/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + prefix + "-" + UUID.randomUUID()
                        + "\",\"companyName\":\"Company\",\"email\":\"" + UUID.randomUUID()
                        + "@example.test\",\"password\":\"StrongPassword123!\"}"));
        return new Auth(response.get("accessToken").asText(), response.get("refreshToken").asText());
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String bearer(String token) { return "Bearer " + token; }
    private String randomCode() { return UUID.randomUUID().toString().replace("-", "").toUpperCase(); }
    private record Auth(String accessToken, String refreshToken) {}
    private record User(String email, String accessToken) {}
    private record Token(String refreshToken) {}
}
