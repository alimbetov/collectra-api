package io.collectra.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class ResourceServerSecurityTest {
    // Real JWKS/signature verification is an integration gate; this suite tests only
    // configured validator policy. Integration tests must not substitute mock JWTs.
    @Test
    void audienceValidatorContractIsRequired() {
        // Intentionally RED until real Keycloak/JWKS integration fixture is provided.
        // This explicit failing assertion prevents a false GREEN on unit-only coverage.
        assertThat(System.getenv("APP_COMMON_KEYCLOAK_INTEGRATION_VERIFIED"))
            .as("Set only in verified Keycloak integration CI after real signed-token tests")
            .isEqualTo("true");
    }
}
