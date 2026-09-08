package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(properties = {
        "collectra.security.platform-bootstrap.enabled=true",
        "collectra.security.platform-bootstrap.email=platform-admin@example.test",
        "collectra.security.platform-bootstrap.password=PlatformPassword123!"
})
class PlatformAdministratorManagementIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    private JsonNode primary;

    @BeforeAll
    void authenticatePrimaryAdministrator() throws Exception {
        primary = login("platform-admin@example.test", "PlatformPassword123!");
    }

    @Test
    void platformAdminCreatesListsAndBlocksAnotherAdministrator() throws Exception {
        String primaryBearer = bearer(primary);
        String email = "second-" + UUID.randomUUID() + "@example.test";
        JsonNode second = read(post("/api/v1/platform/administrators")
                .header("Authorization", primaryBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"SecondPassword123!\"}"));

        JsonNode list = read(get("/api/v1/platform/administrators")
                .header("Authorization", primaryBearer));
        assertThat(list.findValuesAsText("email")).contains(email);

        JsonNode secondTokens = login(email, "SecondPassword123!");
        mockMvc.perform(patch("/api/v1/platform/administrators/{id}/status", second.get("id").asText())
                        .header("Authorization", primaryBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/platform/me").header("Authorization", bearer(secondTokens)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/platform/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(secondTokens.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCannotBlockOrRemoveOwnRole() throws Exception {
        String bearer = bearer(primary);
        String id = subject(primary);

        mockMvc.perform(patch("/api/v1/platform/administrators/{id}/status", id)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/platform/administrators/{id}/role", id)
                        .header("Authorization", bearer))
                .andExpect(status().isConflict());
    }

    @Test
    void passwordChangeRevokesSessionsAndOldPassword() throws Exception {
        String email = "password-" + UUID.randomUUID() + "@example.test";
        JsonNode account = read(post("/api/v1/platform/administrators")
                .header("Authorization", bearer(primary))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"OldPlatformPassword123!\"}"));
        JsonNode oldTokens = login(email, "OldPlatformPassword123!");
        mockMvc.perform(put("/api/v1/platform/administrators/{id}/password",
                                account.get("id").asText())
                        .header("Authorization", bearer(primary))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"ChangedPlatformPassword123!\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/platform/me").header("Authorization", bearer(oldTokens)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"OldPlatformPassword123!\"}"))
                .andExpect(status().isUnauthorized());
        login(email, "ChangedPlatformPassword123!");
    }

    private JsonNode login(String email, String password) throws Exception {
        return read(post("/api/v1/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String subject(JsonNode tokens) {
        try {
            String[] parts = tokens.get("accessToken").asText().split("\\.");
            return json.readTree(java.util.Base64.getUrlDecoder().decode(parts[1])).get("sub").asText();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asText();
    }

    private String tokenBody(String refreshToken) throws Exception {
        return json.writeValueAsString(new TokenRequest(refreshToken));
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private record TokenRequest(String refreshToken) {}
}
