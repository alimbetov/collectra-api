package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.*;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class KeycloakDomainAuthorizationFilterTest {
    private static final String ISSUER = "https://id.example/realms/shared";
    private final VerifiedIdentityLinkRepository links = mock(VerifiedIdentityLinkRepository.class);
    private final GlobalRevocationGate revocation = mock(GlobalRevocationGate.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PlatformUserRoleRepository platformRoles = mock(PlatformUserRoleRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void verifiedLinkWithoutCredentialCutoverCannotBecomeAuthenticatedLocalUser() throws Exception {
        UUID localId = UUID.randomUUID();
        var user = mock(UserAccount.class);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getCredentialAuthority()).thenReturn(UserAccount.CredentialAuthority.LEGACY);
        when(revocation.allows(ISSUER, "global-sub")).thenReturn(true);
        when(links.verifiedLocalUser(ISSUER, "global-sub")).thenReturn(Optional.of(localId));
        when(users.findById(localId)).thenReturn(Optional.of(user));
        var jwt = new Jwt("jwt", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"), Map.of("iss", ISSUER, "sub", "global-sub"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var filter = new KeycloakDomainAuthorizationFilter(links, revocation, users, memberships,
                roles, platformRoles, tenants, true, ISSUER);
        var request = new MockHttpServletRequest("GET", "/api/v1/identity/me");
        request.addHeader("X-Collectra-Tenant-ID", UUID.randomUUID().toString());
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(memberships, roles, platformRoles, tenants);
        verifyNoInteractions(chain);
    }
}
