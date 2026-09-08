package io.collectra.api.shared.security;

import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import io.collectra.api.integration.infrastructure.ServiceClientRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthorizationVersionFilter extends OncePerRequestFilter {
    private final UserAccountRepository users;
    private final TenantMembershipRepository memberships;
    private final ServiceClientRepository clients;
    public AuthorizationVersionFilter(
            UserAccountRepository users,
            TenantMembershipRepository memberships,
            ServiceClientRepository clients) {
        this.users = users;
        this.memberships = memberships;
        this.clients = clients;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            Number version = jwt.getToken().getClaim("authorization_version");
            String type = jwt.getToken().getClaimAsString("token_type");
            boolean valid = "service".equals(type)
                    ? validServiceToken(jwt, version)
                    : "user".equals(type) && validUserToken(jwt, version);
            if (!valid) { SecurityContextHolder.clearContext(); response.sendError(401, "Token is no longer valid"); return; }
        }
        chain.doFilter(request, response);
    }

    private boolean validServiceToken(JwtAuthenticationToken jwt, Number version) {
        try {
            UUID tenantId = UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
            return clients.findById(UUID.fromString(jwt.getToken().getSubject()))
                            .map(client -> version != null && client.active()
                                    && tenantId.equals(client.getTenantId())
                                    && client.getAuthorizationVersion() == version.longValue()).orElse(false)
                    ;
        } catch (RuntimeException invalidClaim) {
            return false;
        }
    }

    private boolean validUserToken(JwtAuthenticationToken jwt, Number version) {
        try {
            UUID userId = UUID.fromString(jwt.getToken().getSubject());
            UUID tenantId = UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
            UUID membershipId = UUID.fromString(jwt.getToken().getClaimAsString("membership_id"));
            boolean userValid = users.findById(userId)
                    .map(user -> version != null && "ACTIVE".equals(user.getStatus())
                            && user.getAuthorizationVersion() == version.longValue())
                    .orElse(false);
            return userValid && memberships.findByIdAndTenantId(membershipId, tenantId)
                    .map(membership -> membership.active() && userId.equals(membership.getUserId()))
                    .orElse(false);
        } catch (RuntimeException invalidClaim) {
            return false;
        }
    }
}
