package io.collectra.api.shared.security;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import io.collectra.api.identity.infrastructure.*;
import io.collectra.api.integration.infrastructure.ServiceClientRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthorizationVersionKeycloakPrincipalTest {
    @Test void globalSubjectIsNotParsedAsLocalUuid() throws Exception {
        var users=mock(UserAccountRepository.class);
        var memberships=mock(TenantMembershipRepository.class);
        var platform=mock(PlatformUserRoleRepository.class);
        var clients=mock(ServiceClientRepository.class);
        var filter=new AuthorizationVersionFilter(users,memberships,platform,clients);
        UUID localId=UUID.randomUUID(),tenantId=UUID.randomUUID(),membershipId=UUID.randomUUID();
        var now=Instant.now();
        var jwt=Jwt.withTokenValue("test").header("alg","RS256")
            .issuer("https://id.example/realms/shared").subject("keycloak-global-subject")
            .issuedAt(now).expiresAt(now.plusSeconds(60))
            .claim("token_type","user").claim("authorization_version",1L)
            .claim("tenant_id",tenantId.toString()).claim("membership_id",membershipId.toString())
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt,List.of(),localId.toString()));
        try {
            var response=new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest(),response,
                (request,resp)->{});
            verify(users).findById(localId);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
