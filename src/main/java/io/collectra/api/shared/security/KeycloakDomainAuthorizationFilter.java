package io.collectra.api.shared.security;

import io.collectra.api.identity.infrastructure.*;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keycloak JWTs are authenticated by JwtDecoder before this filter.
 * This adapter derives all Collectra authorities and tenant claims from its own DB.
 * The tenant header is a selector, NEVER an authorization grant.
 */
@Component
public class KeycloakDomainAuthorizationFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final VerifiedIdentityLinkRepository links;
    private final GlobalRevocationGate revocation;
    private final UserAccountRepository users;
    private final TenantMembershipRepository memberships;
    private final RoleRepository roles;
    private final PlatformUserRoleRepository platformRoles;
    private final TenantRepository tenants;
    private final String issuer;

    public KeycloakDomainAuthorizationFilter(
            VerifiedIdentityLinkRepository links,GlobalRevocationGate revocation,UserAccountRepository users,
            TenantMembershipRepository memberships,RoleRepository roles,
            PlatformUserRoleRepository platformRoles,TenantRepository tenants,
            @Value("${collectra.security.keycloak.enabled:false}") boolean enabled,
            @Value("${collectra.security.keycloak.issuer:}") String issuer) {
        this.links=links;this.revocation=revocation;this.users=users;this.memberships=memberships;
        this.roles=roles;this.platformRoles=platformRoles;this.tenants=tenants;
        this.enabled=enabled;this.issuer=issuer;
    }

    @Override protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(!enabled || !(auth instanceof JwtAuthenticationToken bearer)) {
            chain.doFilter(request,response);return;
        }
        Jwt jwt=bearer.getToken();
        if(!issuer.equals(jwt.getIssuer().toString())) { deny(response);return; }
        try {
            if(!revocation.allows(issuer,jwt.getSubject())) {deny(response);return;}
            var localId=links.verifiedLocalUser(issuer,jwt.getSubject()).orElse(null);
            if(localId==null) {deny(response);return;}
            var user=users.findById(localId).orElse(null);
            if(user==null || !"ACTIVE".equals(user.getStatus())) {deny(response);return;}
            String selected=request.getHeader("X-Collectra-Tenant-ID");
            Map<String,Object> claims=new HashMap<>(jwt.getClaims());
            // Never trust Keycloak role/permission/tenant claims as domain authority.
            for(String key:List.of("roles","permissions","tenant_id","membership_id",
                    "authorization_version","token_type")) claims.remove(key);
            claims.put("authorization_version",user.getAuthorizationVersion());
            Collection<org.springframework.security.core.GrantedAuthority> granted=new ArrayList<>();
            if(selected==null || selected.isBlank()) {
                if(user.getTenantId()!=null || !platformRoles.hasSuperAdminRole(localId)) {
                    deny(response);return;
                }
                claims.put("token_type","platform_user");
                claims.put("roles",platformRoles.roleCodes(localId));
                granted.add(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPER_ADMIN"));
            } else {
                UUID tenantId=UUID.fromString(selected);
                if(!tenantId.equals(user.getTenantId()) || tenants.findById(tenantId).filter(t->t.active()).isEmpty()) {
                    deny(response);return;
                }
                var membership=memberships.findByTenantIdAndUserId(tenantId,localId)
                    .filter(m->m.active()).orElse(null);
                if(membership==null) {deny(response);return;}
                claims.put("token_type","user");
                claims.put("tenant_id",tenantId.toString());
                claims.put("membership_id",membership.getId().toString());
                var roleCodes=roles.findRoleCodes(membership.getId());
                var permissions=roles.findPermissionCodes(membership.getId());
                claims.put("roles",roleCodes);
                claims.put("permissions",permissions);
                granted.add(new SimpleGrantedAuthority("ROLE_HUMAN"));
                roleCodes.forEach(code->granted.add(new SimpleGrantedAuthority("ROLE_"+code)));
                permissions.forEach(code->granted.add(new SimpleGrantedAuthority(code)));
            }
            Jwt local=new Jwt(jwt.getTokenValue(),jwt.getIssuedAt(),jwt.getExpiresAt(),
                jwt.getHeaders(),claims);
            SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(local,granted,localId.toString()));
        } catch(RuntimeException e) {
            SecurityContextHolder.clearContext();
            deny(response);
            return;
        }
        chain.doFilter(request,response);
    }
    private static void deny(HttpServletResponse response)throws IOException {
        SecurityContextHolder.clearContext();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,"Domain authorization denied");
    }
}
