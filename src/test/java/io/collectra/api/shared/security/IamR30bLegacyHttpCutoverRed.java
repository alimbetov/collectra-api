package io.collectra.api.shared.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.collectra.api.identity.api.AuthController;
import io.collectra.api.identity.api.AccountLifecycleController;
import io.collectra.api.identity.api.PlatformAuthController;
import io.collectra.api.identity.application.AuthService;
import io.collectra.api.identity.application.PlatformAuthService;
import io.collectra.api.identity.application.UserLifecycleService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * IAM-R3.0B HTTP RED. Standalone MVC verifies the HTTP boundary contract only.
 * It intentionally expects KEYCLOAK_ONLY to reject legacy credential endpoints,
 * while current controllers still delegate to the legacy services.
 *
 * Run explicitly: mvn -Dtest=IamR30bLegacyHttpCutoverRed test
 * A later GREEN test MUST use the full Spring Security filter chain, actual persistence,
 * and real Keycloak; passing these standalone tests alone is not release evidence.
 */
class IamR30bLegacyHttpCutoverRed {
    @Test
    void keycloakOnlyMustRejectLegacyTenantPasswordLogin() throws Exception {
        AuthService service = mock(AuthService.class);
        UUID tenant = UUID.randomUUID();
        when(service.login(eq(tenant), anyString(), anyString()))
                .thenReturn(new AuthService.AuthTokens("legacy-access", "legacy-refresh", "Bearer", 300));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(service)).build();
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Test-Credential-Authority", "KEYCLOAK_ONLY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenant + "\",\"email\":\"test@example.invalid\",\"password\":\"legacy-password\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void keycloakOnlyMustRejectLegacyRefresh() throws Exception {
        AuthService service = mock(AuthService.class);
        when(service.refresh(anyString()))
                .thenReturn(new AuthService.AuthTokens("legacy-access", "legacy-refresh", "Bearer", 300));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(service)).build();
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Test-Credential-Authority", "KEYCLOAK_ONLY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"legacy-refresh\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void keycloakOnlyMustRejectLegacyPasswordReset() throws Exception {
        UserLifecycleService service = mock(UserLifecycleService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AccountLifecycleController(service)).build();
        mvc.perform(post("/api/v1/auth/password/reset")
                        .header("X-Test-Credential-Authority", "KEYCLOAK_ONLY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"legacy-reset-token\",\"newPassword\":\"new-password-12345\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void keycloakOnlyMustRejectLegacyPlatformPasswordLogin() throws Exception {
        PlatformAuthService service = mock(PlatformAuthService.class);
        when(service.login(anyString(), anyString()))
                .thenReturn(new PlatformAuthService.PlatformTokens("legacy-access", "legacy-refresh", "Bearer", 300));
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformAuthController(service)).build();
        mvc.perform(post("/api/v1/platform/auth/login")
                        .header("X-Test-Credential-Authority", "KEYCLOAK_ONLY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"operator@example.invalid\",\"password\":\"legacy-password\"}"))
                .andExpect(status().isGone());
    }
}
