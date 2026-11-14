package io.collectra.api.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.collectra.api.identity.application.UserLifecycleService;
import io.collectra.api.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserControllerPrincipalTest {
    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void usesVerifiedLocalPrincipalInsteadOfKeycloakSubject() {
        UUID localUser = UUID.randomUUID();
        UUID externalSubject = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID membership = UUID.randomUUID();
        Jwt jwt =
                Jwt.withTokenValue("test")
                        .header("alg", "RS256")
                        .subject(externalSubject.toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .claim("membership_id", membership.toString())
                        .build();
        TenantContext.set(tenant);
        UserLifecycleService lifecycle = mock(UserLifecycleService.class);

        new CurrentUserController(lifecycle)
                .me(new JwtAuthenticationToken(jwt, List.of(), localUser.toString()));

        verify(lifecycle).me(localUser, tenant, membership);
    }

    @Test
    void platformProfileUsesVerifiedLocalPrincipal() {
        UUID localUser = UUID.randomUUID();
        Jwt jwt =
                Jwt.withTokenValue("test")
                        .header("alg", "RS256")
                        .subject(UUID.randomUUID().toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();

        var profile =
                new PlatformAccessController()
                        .me(new JwtAuthenticationToken(jwt, List.of(), localUser.toString()));

        assertThat(profile.userId()).isEqualTo(localUser);
    }
}
