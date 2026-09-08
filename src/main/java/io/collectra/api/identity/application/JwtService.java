package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.TenantMembership;
import io.collectra.api.identity.domain.UserAccount;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, @Value("${collectra.security.access-token-ttl:PT15M}") Duration ttl) {
        this.encoder = encoder; this.ttl = ttl;
    }

    public String issue(UserAccount user, TenantMembership membership, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("collectra-api").issuedAt(now)
                .expiresAt(now.plus(ttl)).subject(user.getId().toString()).audience(List.of("collectra-api"))
                .claim("tenant_id", membership.getTenantId().toString())
                .claim("membership_id", membership.getId().toString()).claim("roles", roles)
                .claim("permissions", permissions).claim("authorization_version", user.getAuthorizationVersion())
                .claim("token_type", "user").build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public String issueService(UUID subjectId, UUID tenantId, String clientId, List<String> scopes,
            Duration serviceTtl, long authorizationVersion) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("collectra-api").issuedAt(now)
                .expiresAt(now.plus(serviceTtl)).subject(subjectId.toString()).audience(List.of("collectra-api"))
                .claim("tenant_id", tenantId.toString()).claim("client_id", clientId)
                .claim("scope", String.join(" ", scopes)).claim("authorization_version", authorizationVersion)
                .claim("token_type", "service").build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public long expiresIn() { return ttl.toSeconds(); }
}
