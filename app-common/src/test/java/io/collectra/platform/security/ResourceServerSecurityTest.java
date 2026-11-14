package io.collectra.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ResourceServerSecurityTest {
    private Jwt tokenWithAudience(String... audiences) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("unit-test-token")
            .header("alg", "RS256")
            .issuer("https://id.example/realms/shared")
            .subject("global-subject-1")
            .audience(List.of(audiences))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
    }

    @Test
    void acceptsCorrectAudience() {
        var result = ResourceServerSecurity.requiredAudience("app-common-api")
            .validate(tokenWithAudience("app-common-api"));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsCollectraTokenAtAppCommonApi() {
        var result = ResourceServerSecurity.requiredAudience("app-common-api")
            .validate(tokenWithAudience("collectra-api"));
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingAudience() {
        var result = ResourceServerSecurity.requiredAudience("app-common-api")
            .validate(tokenWithAudience());
        assertThat(result.hasErrors()).isTrue();
    }
}
