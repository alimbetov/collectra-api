package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.identity.application.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IAM-R3.0B full Spring Boot + Liquibase + PostgreSQL + Spring Security RED.
 *
 * <p>Run explicitly: mvn -Dtest=IamR30bFullContextCutoverRed test
 *
 * <p>Mode is server-side configuration; the test must NOT supply a mode header.
 * Existing users are created via the current service to retain real UUIDs and
 * refresh sessions; the HTTP boundary must stop issuing legacy credentials in
 * KEYCLOAK_ONLY. Expected RED until the mode and enforcement are implemented.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {\n        "collectra.security.credential-authority-mode=KEYCLOAK_ONLY",\n        "collectra.security.keycloak.enabled=true",\n        "collectra.security.keycloak.issuer=http://localhost:18080/realms/test",\n        "collectra.security.keycloak.jwk-set-uri=http://localhost:18080/realms/test/protocol/openid-connect/certs"\n})
class IamR30bFullContextCutoverRed extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;

    @Test
    void existingTenantUserCannotLoginWithLegacyPasswordInKeycloakOnly() throws Exception {
        String slug = "r30b-" + UUID.randomUUID();
        String email = "migration-" + UUID.randomUUID() + "@example.invalid";
        AuthService.AuthTokens existing = auth.register(slug, "Migration Test", email, "StrongPassword123!");
        assertThat(existing.accessToken()).isNotBlank();
        var response = mvc.perform(post("/api/v1/auth/login/by-slug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Login(slug, email, "StrongPassword123!"))))
                .andReturn().getResponse();
        assertThat(response.getStatus())
                .as("KEYCLOAK_ONLY must not issue a legacy JWT for a real existing user")
                .isBetween(400, 499);
    }

    @Test
    void existingRefreshTokenCannotBeExchangedInKeycloakOnly() throws Exception {
        String slug = "r30b-" + UUID.randomUUID();
        AuthService.AuthTokens existing = auth.register(slug, "Migration Test",
                "refresh-" + UUID.randomUUID() + "@example.invalid", "StrongPassword123!");
        var response = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Refresh(existing.refreshToken()))))
                .andReturn().getResponse();
        assertThat(response.getStatus())
                .as("KEYCLOAK_ONLY must reject a previously issued legacy refresh token")
                .isBetween(400, 499);
    }

    record Login(String tenantSlug, String email, String password) {}
    record Refresh(String refreshToken) {}
}
