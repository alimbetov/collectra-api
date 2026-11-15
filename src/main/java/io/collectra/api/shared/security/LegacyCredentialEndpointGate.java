package io.collectra.api.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fail-closed server-side cutover gate. The legacy mode remains the default until
 * an operator explicitly enables Keycloak-only. No client-controlled override.
 */
@Component
public class LegacyCredentialEndpointGate extends OncePerRequestFilter {
    private static final Set<String> LEGACY_ENDPOINTS = Set.of(
            "/api/v1/auth/tenants/register",
            "/api/v1/auth/login",
            "/api/v1/auth/login/by-slug",
            "/api/v1/auth/refresh",
            "/api/v1/auth/invitations/accept",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/platform/auth/login",
            "/api/v1/platform/auth/refresh");
    private final CredentialAuthorityMode mode;

    public LegacyCredentialEndpointGate(
            @Value("${collectra.security.credential-authority-mode:LEGACY}") CredentialAuthorityMode mode) {
        this.mode = mode;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (mode == CredentialAuthorityMode.KEYCLOAK_ONLY
                && "POST".equalsIgnoreCase(request.getMethod())
                && LEGACY_ENDPOINTS.contains(request.getServletPath())) {
            response.sendError(HttpServletResponse.SC_GONE, "Legacy credential endpoint disabled");
            return;
        }
        chain.doFilter(request, response);
    }
}
