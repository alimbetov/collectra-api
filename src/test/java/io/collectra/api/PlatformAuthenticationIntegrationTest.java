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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "collectra.security.platform-bootstrap.enabled=true",
        "collectra.security.platform-bootstrap.email=platform-admin@example.test",
        "collectra.security.platform-bootstrap.password=PlatformPassword123!"
})
class PlatformAuthenticationIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder jwtDecoder;

    @Test
    void bootstrapIsIdempotentAndPlatformLoginIssuesTenantlessToken() throws Exception {
        JsonNode tokens = login();
        var jwt = jwtDecoder.decode(tokens.get("accessToken").asText());

        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("platform_user");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("PLATFORM_SUPER_ADMIN");
        assertThat(jwt.hasClaim("tenant_id")).isFalse();
        assertThat(jwt.hasClaim("membership_id")).isFalse();

        String bearer = "Bearer " + tokens.get("accessToken").asText();
        mockMvc.perform(get("/api/v1/platform/me").header("Authorization", bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/identity/me").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/integration/service-clients").header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesPlatformFamily() throws Exception {
        JsonNode first = login();
        JsonNode second = read(post("/api/v1/platform/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tokenBody(first.get("refreshToken").asText())));

        mockMvc.perform(post("/api/v1/platform/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(first.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/platform/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(second.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformRefreshTokenCannotBeUsedByTenantRefreshEndpoint() throws Exception {
        JsonNode tokens = login();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(tokens.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPlatformCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"platform-admin@example.test\",\"password\":\"WrongPassword123!\"}"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode login() throws Exception {
        return read(post("/api/v1/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"platform-admin@example.test\",\"password\":\"PlatformPassword123!\"}"));
    }

    private JsonNode read(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String tokenBody(String refreshToken) throws Exception {
        return json.writeValueAsString(new TokenRequest(refreshToken));
    }

    private record TokenRequest(String refreshToken) {}
}
