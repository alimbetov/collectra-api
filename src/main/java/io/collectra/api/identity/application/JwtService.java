package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final Duration ttl;

    public JwtService(
            JwtEncoder encoder,
            @Value("${collectra.security.access-token-ttl:PT15M}") Duration ttl) {
        this.encoder = encoder;
        this.ttl = ttl;
    }

    public String issue(UserAccount user) {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer("collectra-api")
                        .issuedAt(now)
                        .expiresAt(now.plus(ttl))
                        .subject(user.getId().toString())
                        .audience(List.of("collectra-api"))
                        .claim("tenant_id", user.getTenantId().toString())
                        .claim("roles", List.of(user.getRole().name()))
                        .claim("token_type", "user")
                        .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public long expiresIn() {
        return ttl.toSeconds();
    }
}
