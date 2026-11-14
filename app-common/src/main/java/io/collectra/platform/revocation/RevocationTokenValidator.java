package io.collectra.platform.revocation;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Interim fail-closed check for App Common only. PostgreSQL is consulted on every
 * authenticated request. Not a distributed snapshot and not cross-app enforcement.
 */
@Component
public class RevocationTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final SubjectAccessService access;
    public RevocationTokenValidator(SubjectAccessService access) { this.access = access; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            // Registered machine clients are authenticated by signature, issuer, audience,
            // scope and their enabled OAuth binding; they are not human access subjects.
            String client = jwt.getClaimAsString("azp");
            if (client != null && !client.isBlank()) {
                if (!access.isEnabledConsumerClient(client)) return deny();
                return OAuth2TokenValidatorResult.success();
            }
            if (!access.isAllowed(jwt.getIssuer().toString(), jwt.getSubject())) {
                return deny();
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException ex) {
            // A database outage must never be treated as 'not blocked'.
            return deny();
        }
    }

    private OAuth2TokenValidatorResult deny() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
            "invalid_token", "Global access state unavailable or blocked", null));
    }
}
