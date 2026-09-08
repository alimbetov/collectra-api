package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TenantUserLifecycleIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder jwtDecoder;

    @Test
    void tenantAdminInvitesUserAndUserCompletesLifecycle() throws Exception {
        Auth admin = register("lifecycle-admin");
        String userEmail = "user-" + UUID.randomUUID() + "@example.test";
        JsonNode invitation =
                read(
                        post("/api/v1/identity/invitations")
                                .header("Authorization", bearer(admin.accessToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + userEmail + "\"}"));
        assertThat(invitation.get("developmentToken").asText()).isNotBlank();

        JsonNode accepted =
                read(
                        post("/api/v1/auth/invitations/accept")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + invitation.get("developmentToken").asText()
                                                + "\",\"password\":\"UserPassword123!\","
                                                + "\"displayName\":\"Tenant User\","
                                                + "\"locale\":\"ru\",\"timezone\":\"Asia/Almaty\"}"));
        String userAccess = accepted.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/identity/me").header("Authorization", bearer(userAccess)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userEmail))
                .andExpect(jsonPath("$.displayName").value("Tenant User"))
                .andExpect(jsonPath("$.roles[0]").value("TENANT_USER"));

        mockMvc.perform(
                        post("/api/v1/auth/invitations/accept")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + invitation.get("developmentToken").asText()
                                                + "\",\"password\":\"UserPassword123!\","
                                                + "\"displayName\":\"Again\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanUpdateProfileChangePasswordAndManageSessions() throws Exception {
        UserContext user = invitedUser();
        mockMvc.perform(
                        patch("/api/v1/identity/me")
                                .header("Authorization", bearer(user.accessToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"displayName\":\"Updated User\","
                                                + "\"locale\":\"kk\",\"timezone\":\"Asia/Almaty\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated User"))
                .andExpect(jsonPath("$.locale").value("kk"));

        JsonNode sessions =
                read(
                        get("/api/v1/identity/me/sessions")
                                .header("Authorization", bearer(user.accessToken())));
        assertThat(sessions).isNotEmpty();

        mockMvc.perform(
                        post("/api/v1/identity/me/change-password")
                                .header("Authorization", bearer(user.accessToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"UserPassword123!\","
                                                + "\"newPassword\":\"NewUserPassword123!\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/identity/me").header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isUnauthorized());

        login(user.tenantId(), user.email(), "UserPassword123!")
                .andExpect(status().isUnauthorized());
        login(user.tenantId(), user.email(), "NewUserPassword123!")
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetIsSingleUseAndDoesNotRevealUnknownEmail() throws Exception {
        UserContext user = invitedUser();
        JsonNode reset =
                read(
                        post("/api/v1/auth/password/forgot")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"tenantId\":\""
                                                + user.tenantId()
                                                + "\",\"email\":\""
                                                + user.email()
                                                + "\"}"));
        String token = reset.get("developmentToken").asText();
        mockMvc.perform(
                        post("/api/v1/auth/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + token
                                                + "\",\"newPassword\":\"ResetPassword123!\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        post("/api/v1/auth/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + token
                                                + "\",\"newPassword\":\"AnotherPassword123!\"}"))
                .andExpect(status().isUnauthorized());
        login(user.tenantId(), user.email(), "ResetPassword123!")
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/auth/password/forgot")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"tenantId\":\""
                                                + user.tenantId()
                                                + "\",\"email\":\"unknown@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.developmentToken").doesNotExist());
    }

    @Test
    void anotherTenantCannotRevokeInvitation() throws Exception {
        Auth first = register("invite-first");
        Auth second = register("invite-second");
        JsonNode invitation =
                read(
                        post("/api/v1/identity/invitations")
                                .header("Authorization", bearer(first.accessToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"isolated@example.test\"}"));

        mockMvc.perform(
                        delete("/api/v1/identity/invitations/{id}", invitation.get("id").asText())
                                .header("Authorization", bearer(second.accessToken())))
                .andExpect(status().isNotFound());
    }

    private UserContext invitedUser() throws Exception {
        Auth admin = register("invite-user");
        UUID tenantId =
                UUID.fromString(
                        jwtDecoder.decode(admin.accessToken()).getClaimAsString("tenant_id"));
        String email = "member-" + UUID.randomUUID() + "@example.test";
        JsonNode invitation =
                read(
                        post("/api/v1/identity/invitations")
                                .header("Authorization", bearer(admin.accessToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\"}"));
        JsonNode accepted =
                read(
                        post("/api/v1/auth/invitations/accept")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + invitation.get("developmentToken").asText()
                                                + "\",\"password\":\"UserPassword123!\","
                                                + "\"displayName\":\"User\"}"));
        return new UserContext(tenantId, email, accepted.get("accessToken").asText());
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
                                                + "\",\"companyName\":\"Company\","
                                                + "\"email\":\""
                                                + UUID.randomUUID()
                                                + "@example.test\","
                                                + "\"password\":\"StrongPassword123!\"}"));
        return new Auth(response.get("accessToken").asText());
    }

    private org.springframework.test.web.servlet.ResultActions login(
            UUID tenantId, String email, String password) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tenantId\":\""
                                        + tenantId
                                        + "\",\"email\":\""
                                        + email
                                        + "\",\"password\":\""
                                        + password
                                        + "\"}"));
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

    private record Auth(String accessToken) {}

    private record UserContext(UUID tenantId, String email, String accessToken) {}
}
