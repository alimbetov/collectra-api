package io.collectra.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class IamOidcControllerTest {
    @Test void publishesCodeAndPkceWithoutSecrets() {
        var controller = new IamOidcController("https://id.example/realms/shared", "collectra-web");
        var response = controller.config();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("responseType", "code")
                .containsEntry("pkceMethod", "S256")
                .containsEntry("discoveryUrl",
                        "https://id.example/realms/shared/.well-known/openid-configuration");
        assertThat(response.getBody()).doesNotContainKeys("clientSecret", "refreshToken", "password");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test void rejectsInsecureNonlocalIssuer() {
        assertThrows(IllegalArgumentException.class,
                () -> new IamOidcController("http://id.example/realms/shared", "collectra-web"));
    }

    @Test void sessionReturnsJwtIdentityWithoutCredentialMaterial() {
        var controller = new IamOidcController("https://id.example/realms/shared", "collectra-web");
        Jwt jwt = new Jwt("sensitive-token", Instant.now(), Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "RS256"),
                java.util.Map.of("sub", "user-123", "iss", "https://id.example/realms/shared",
                        "aud", List.of("app-common-api")));
        var response = controller.session(jwt);
        assertThat(response.getBody()).containsEntry("authenticated", true)
                .containsEntry("subject", "user-123");
        assertThat(response.getBody()).doesNotContainKeys("accessToken", "refreshToken", "password");
    }
}
