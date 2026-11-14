package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

class KeycloakModeSecurityTest {
    @Test void keycloakRolesCannotBecomeCollectraTenantRoles() {
        var config=new SecurityConfig();
        var now=Instant.now();
        Jwt token=Jwt.withTokenValue("test").header("alg","RS256")
            .issuer("https://id.example/realms/shared").subject("global-user")
            .audience(List.of("collectra-api")).issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("roles",List.of("PLATFORM_SUPER_ADMIN"))
            .claim("token_type","user").build();
        @SuppressWarnings("unchecked")
        java.util.Collection<org.springframework.security.core.GrantedAuthority> roles=
            (java.util.Collection<org.springframework.security.core.GrantedAuthority>)
                ReflectionTestUtils.invokeMethod(config,"extractAuthorities",token);
        assertThat(roles).isEmpty();
    }
}
