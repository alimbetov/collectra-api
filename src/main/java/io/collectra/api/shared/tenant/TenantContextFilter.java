package io.collectra.api.shared.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        try {
            if (auth instanceof JwtAuthenticationToken jwt
                    && jwt.getToken().hasClaim("tenant_id")) {
                TenantContext.set(UUID.fromString(jwt.getToken().getClaimAsString("tenant_id")));
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
