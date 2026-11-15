package io.collectra.api.shared.security;

import io.collectra.api.identity.infrastructure.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gate for future Keycloak JWT integration. This does not issue permissions. No automatic tenant
 * membership or UUID replacement is permitted.
 */
@Component
public class KeycloakIdentityGate {
    private final VerifiedIdentityLinkRepository links;
    private final UserAccountRepository users;
    private final String issuer;

    public KeycloakIdentityGate(
            VerifiedIdentityLinkRepository links,
            UserAccountRepository users,
            @Value("${collectra.security.keycloak.issuer:}") String issuer) {
        this.links = links;
        this.users = users;
        this.issuer = issuer;
    }

    public boolean isVerifiedActive(String tokenIssuer, String tokenSubject) {
        if (issuer.isBlank() || !issuer.equals(tokenIssuer)) return false;
        return links.verifiedLocalUser(tokenIssuer, tokenSubject)
                .flatMap(users::findById)
                .map(user -> "ACTIVE".equals(user.getStatus())
                        && user.getCredentialAuthority() == io.collectra.api.identity.domain.UserAccount.CredentialAuthority.KEYCLOAK)
                .orElse(false);
    }
}
