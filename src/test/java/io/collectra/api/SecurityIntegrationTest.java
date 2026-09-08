package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void tenantAdminCanReadOnlyOwnTenantMembers() throws Exception {
        Auth first = register("smoke-first-" + UUID.randomUUID(), "first@example.test");
        Auth second = register("smoke-second-" + UUID.randomUUID(), "second@example.test");

        JsonNode secondUsers = read(get("/api/v1/identity/users")
                .header("Authorization", "Bearer " + second.accessToken));
        String secondMembership = secondUsers.get(0).get("id").asText();

        mockMvc.perform(put("/api/v1/identity/memberships/{id}/roles", secondMembership)
                        .header("Authorization", "Bearer " + first.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"00000000-0000-0000-0000-000000000003\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshTokenIsRotatedAndReuseRevokesFamily() throws Exception {
        Auth auth = register("refresh-" + UUID.randomUUID(), "refresh@example.test");
        JsonNode rotated = read(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Token(auth.refreshToken))));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Token(auth.refreshToken))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Token(rotated.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceJwtCannotCallHumanPermissionEndpoint() throws Exception {
        Auth admin = register("service-" + UUID.randomUUID(), "service@example.test");
        String clientSecret = "tenant-owned-service-secret-123456789";
        JsonNode client = read(post("/api/v1/integration/service-clients")
                .header("Authorization", "Bearer " + admin.accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"erp-smoke\",\"name\":\"ERP\",\"clientSecret\":\""
                        + clientSecret + "\",\"scopes\":[\"integration:imports:read\"]}"));
        assertThat(client.has("clientSecret")).isFalse();
        JsonNode token = read(post("/api/v1/integration/service-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"erp-smoke\",\"clientSecret\":\"" + clientSecret
                        + "\",\"scopes\":[\"integration:imports:read\"]}"));
        mockMvc.perform(get("/api/v1/identity/users")
                        .header("Authorization", "Bearer " + token.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    void otpIsSingleUse() throws Exception {
        Auth auth = register("otp-" + UUID.randomUUID(), "otp@example.test");
        JsonNode challenge = read(post("/api/v1/auth/otp/challenges")
                .header("Authorization", "Bearer " + auth.accessToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"LOGOUT_ALL\"}"));
        String path = "/api/v1/auth/otp/challenges/" + challenge.get("id").asText() + "/verify";
        String body = "{\"code\":\"" + challenge.get("developmentCode").asText() + "\"}";
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(false));
    }

    private Auth register(String slug, String email) throws Exception {
        JsonNode response = read(post("/api/v1/auth/tenants/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + slug + "\",\"companyName\":\"Smoke Company\",\"email\":\""
                        + email + "\",\"password\":\"StrongPassword123!\"}"));
        return new Auth(response.get("accessToken").asText(), response.get("refreshToken").asText());
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private record Auth(String accessToken, String refreshToken) {}
    private record Token(String refreshToken) {}
}
