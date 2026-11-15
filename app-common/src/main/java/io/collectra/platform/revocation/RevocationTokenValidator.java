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
            String scopes = jwt.getClaimAsString("scope");
            boolean consumerScope = scopes != null && java.util.Arrays.stream(scopes.split(" "))
                .anyMatch(s -> s.equals("revocations.read") || s.equals("revocations.ack"));
            if (client != null && !client.isBlank() && consumerScope) {
                // A human token with an azp and revocation scopes is not a machine credential.
                // Keycloak client_credentials tokens carry the service-account username.
                String username = jwt.getClaimAsString("preferred_username");
                if (!("service-account-" + client).equals(username)) return deny();
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
