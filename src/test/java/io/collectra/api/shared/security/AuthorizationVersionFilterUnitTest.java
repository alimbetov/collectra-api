package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.identity.domain.TenantMembership;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.PlatformUserRoleRepository;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.integration.domain.ServiceClient;
import io.collectra.api.integration.infrastructure.ServiceClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthorizationVersionFilterUnitTest {

    private UserAccountRepository users;
    private TenantMembershipRepository memberships;
    private PlatformUserRoleRepository platformRoles;
    private ServiceClientRepository clients;
    private AuthorizationVersionFilter filter;

    @BeforeEach
    void setUp() {
        users = mock(UserAccountRepository.class);
        memberships = mock(TenantMembershipRepository.class);
        platformRoles = mock(PlatformUserRoleRepository.class);
        clients = mock(ServiceClientRepository.class);
        filter = new AuthorizationVersionFilter(users, memberships, platformRoles, clients);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeUserWithMatchingVersionAndMembershipPasses() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        TenantMembership membership = mock(TenantMembership.class);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getAuthorizationVersion()).thenReturn(7L);
        when(membership.active()).thenReturn(true);
        when(membership.getUserId()).thenReturn(userId);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(memberships.findByIdAndTenantId(membershipId, tenantId))
                .thenReturn(Optional.of(membership));
        authenticate(userJwt(userId, tenantId, membershipId, 7L));
        FilterChain chain = mock(FilterChain.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        filter.doFilter(mock(HttpServletRequest.class), response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(response, never()).sendError(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void staleUserAuthorizationVersionIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getAuthorizationVersion()).thenReturn(8L);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        authenticate(userJwt(userId, UUID.randomUUID(), UUID.randomUUID(), 7L));

        assertRejected();
    }

    @Test
    void inactiveMembershipIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        TenantMembership membership = mock(TenantMembership.class);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getAuthorizationVersion()).thenReturn(1L);
        when(membership.active()).thenReturn(false);
        when(membership.getUserId()).thenReturn(userId);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(memberships.findByIdAndTenantId(membershipId, tenantId))
                .thenReturn(Optional.of(membership));
        authenticate(userJwt(userId, tenantId, membershipId, 1L));

        assertRejected();
    }

    @Test
    void membershipOwnedByAnotherUserIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        TenantMembership membership = mock(TenantMembership.class);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getAuthorizationVersion()).thenReturn(1L);
        when(membership.active()).thenReturn(true);
        when(membership.getUserId()).thenReturn(UUID.randomUUID());
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(memberships.findByIdAndTenantId(membershipId, tenantId))
                .thenReturn(Optional.of(membership));
        authenticate(userJwt(userId, tenantId, membershipId, 1L));

        assertRejected();
    }

    @Test
    void malformedUserClaimsAreRejectedInsteadOfEscapingFilter() throws Exception {
        authenticate(jwt(Map.of(
                "sub", "not-a-uuid",
                "token_type", "user",
                "tenant_id", "also-bad",
                "membership_id", "bad",
                "authorization_version", 1L)));

        assertRejected();
    }

    @Test
    void activeServiceClientWithMatchingTenantAndVersionPasses() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ServiceClient client = mock(ServiceClient.class);
        when(client.active()).thenReturn(true);
        when(client.getTenantId()).thenReturn(tenantId);
        when(client.getAuthorizationVersion()).thenReturn(4L);
        when(clients.findById(clientId)).thenReturn(Optional.of(client));
        authenticate(serviceJwt(clientId, tenantId, 4L));
        FilterChain chain = mock(FilterChain.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        filter.doFilter(mock(HttpServletRequest.class), response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(response, never()).sendError(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void serviceTokenTenantMismatchIsRejected() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID storedTenantId = UUID.randomUUID();
        ServiceClient client = mock(ServiceClient.class);
        when(client.active()).thenReturn(true);
        when(client.getTenantId()).thenReturn(storedTenantId);
        when(client.getAuthorizationVersion()).thenReturn(4L);
        when(clients.findById(clientId)).thenReturn(Optional.of(client));
        authenticate(serviceJwt(clientId, UUID.randomUUID(), 4L));

        assertRejected();
    }

    @Test
    void staleServiceAuthorizationVersionIsRejected() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ServiceClient client = mock(ServiceClient.class);
        when(client.active()).thenReturn(true);
        when(client.getTenantId()).thenReturn(tenantId);
        when(client.getAuthorizationVersion()).thenReturn(5L);
        when(clients.findById(clientId)).thenReturn(Optional.of(client));
        authenticate(serviceJwt(clientId, tenantId, 4L));

        assertRejected();
    }

    @Test
    void platformAdministratorRequiresTenantlessActiveUserAndSuperAdminRole() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        when(user.getTenantId()).thenReturn(null);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getAuthorizationVersion()).thenReturn(3L);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(platformRoles.hasSuperAdminRole(userId)).thenReturn(true);
        authenticate(platformJwt(userId, 3L));
        FilterChain chain = mock(FilterChain.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        filter.doFilter(mock(HttpServletRequest.class), response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void platformUserWithTenantIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        when(user.getTenantId()).thenReturn(UUID.randomUUID());
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getAuthorizationVersion()).thenReturn(3L);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(platformRoles.hasSuperAdminRole(userId)).thenReturn(true);
        authenticate(platformJwt(userId, 3L));

        assertRejected();
    }

    @Test
    void platformUserWithoutSuperAdminRoleIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        when(user.getTenantId()).thenReturn(null);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getAuthorizationVersion()).thenReturn(3L);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(platformRoles.hasSuperAdminRole(userId)).thenReturn(false);
        authenticate(platformJwt(userId, 3L));

        assertRejected();
    }

    @Test
    void unknownTokenTypeIsRejected() throws Exception {
        authenticate(jwt(Map.of(
                "sub", UUID.randomUUID().toString(),
                "token_type", "mystery",
                "authorization_version", 1L)));

        assertRejected();
    }

    private void assertRejected() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(mock(HttpServletRequest.class), response, chain);

        verify(response).sendError(401, "Token is no longer valid");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private Jwt userJwt(UUID userId, UUID tenantId, UUID membershipId, long version) {
        return jwt(Map.of(
                "sub", userId.toString(),
                "token_type", "user",
                "tenant_id", tenantId.toString(),
                "membership_id", membershipId.toString(),
                "authorization_version", version));
    }

    private Jwt serviceJwt(UUID clientId, UUID tenantId, long version) {
        return jwt(Map.of(
                "sub", clientId.toString(),
                "token_type", "service",
                "tenant_id", tenantId.toString(),
                "authorization_version", version));
    }

    private Jwt platformJwt(UUID userId, long version) {
        return jwt(Map.of(
                "sub", userId.toString(),
                "token_type", "platform_user",
                "authorization_version", version));
    }

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
