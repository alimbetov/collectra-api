package io.collectra.api.shared.tenant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantContextFilterUnitTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void cleanContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void populatesTenantFromJwtAndClearsItAfterSuccessfulRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(jwtWithTenant(tenantId.toString()));
        AtomicReference<UUID> observedTenant = new AtomicReference<>();

        filter.doFilter(
                mock(HttpServletRequest.class),
                mock(HttpServletResponse.class),
                (request, response) -> observedTenant.set(TenantContext.requireTenantId()));

        org.assertj.core.api.Assertions.assertThat(observedTenant.get()).isEqualTo(tenantId);
        assertTenantContextIsEmpty();
    }

    @Test
    void clearsTenantWhenFilterChainThrows() {
        UUID tenantId = UUID.randomUUID();
        authenticate(jwtWithTenant(tenantId.toString()));

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        mock(HttpServletRequest.class),
                                        mock(HttpServletResponse.class),
                                        (request, response) -> {
                                            org.assertj.core.api.Assertions.assertThat(
                                                            TenantContext.requireTenantId())
                                                    .isEqualTo(tenantId);
                                            throw new IllegalStateException("downstream failure");
                                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream failure");

        assertTenantContextIsEmpty();
    }

    @Test
    void requestWithoutTenantClaimCannotReusePreviousTenant() throws Exception {
        UUID firstTenant = UUID.randomUUID();
        authenticate(jwtWithTenant(firstTenant.toString()));
        filter.doFilter(
                mock(HttpServletRequest.class),
                mock(HttpServletResponse.class),
                (request, response) ->
                        org.assertj.core.api.Assertions.assertThat(TenantContext.requireTenantId())
                                .isEqualTo(firstTenant));

        authenticate(jwtWithoutTenant());
        AtomicReference<Throwable> missingTenant = new AtomicReference<>();
        filter.doFilter(
                mock(HttpServletRequest.class),
                mock(HttpServletResponse.class),
                (request, response) -> {
                    try {
                        TenantContext.requireTenantId();
                    } catch (Throwable error) {
                        missingTenant.set(error);
                    }
                });

        org.assertj.core.api.Assertions.assertThat(missingTenant.get())
                .isInstanceOf(MissingTenantException.class);
        assertTenantContextIsEmpty();
    }

    @Test
    void malformedTenantClaimDoesNotLeakExistingThreadLocalValue() {
        UUID leakedValue = UUID.randomUUID();
        TenantContext.set(leakedValue);
        authenticate(jwtWithTenant("not-a-uuid"));

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        mock(HttpServletRequest.class),
                                        mock(HttpServletResponse.class),
                                        mock(FilterChain.class)))
                .isInstanceOf(IllegalArgumentException.class);

        assertTenantContextIsEmpty();
    }

    @Test
    void tenantHeaderCannotOverrideJwtTenant() throws Exception {
        UUID tokenTenant = UUID.randomUUID();
        UUID headerTenant = UUID.randomUUID();
        authenticate(jwtWithTenant(tokenTenant.toString()));
        HttpServletRequest request = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getHeader("X-Tenant-Id")).thenReturn(headerTenant.toString());
        AtomicReference<UUID> observedTenant = new AtomicReference<>();

        filter.doFilter(
                request,
                mock(HttpServletResponse.class),
                (ignoredRequest, response) -> observedTenant.set(TenantContext.requireTenantId()));

        org.assertj.core.api.Assertions.assertThat(observedTenant.get()).isEqualTo(tokenTenant);
        verify(request).getHeader("X-Tenant-Id");
        assertTenantContextIsEmpty();
    }

    private void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private Jwt jwtWithTenant(String tenantId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId)
                .build();
    }

    private Jwt jwtWithoutTenant() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .build();
    }

    private void assertTenantContextIsEmpty() {
        assertThatThrownBy(TenantContext::requireTenantId).isInstanceOf(MissingTenantException.class);
    }
}
