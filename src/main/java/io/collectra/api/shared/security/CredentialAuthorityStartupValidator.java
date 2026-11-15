package io.collectra.api.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/** Reject dangerous configuration rather than silently reverting to local passwords. */
@Component
public class CredentialAuthorityStartupValidator {
    private final CredentialAuthorityMode mode;
    private final boolean keycloakEnabled;

    public CredentialAuthorityStartupValidator(
            @Value("${collectra.security.credential-authority-mode:LEGACY}") CredentialAuthorityMode mode,
            @Value("${collectra.security.keycloak.enabled:false}") boolean keycloakEnabled) {
        this.mode = mode;
        this.keycloakEnabled = keycloakEnabled;
    }

    @PostConstruct
    public void validate() {
        if (mode == CredentialAuthorityMode.KEYCLOAK_ONLY && !keycloakEnabled) {
            throw new IllegalStateException(
                    "KEYCLOAK_ONLY requires collectra.security.keycloak.enabled=true");
        }
        if (mode == CredentialAuthorityMode.MIGRATION && !keycloakEnabled) {
            throw new IllegalStateException(
                    "MIGRATION requires collectra.security.keycloak.enabled=true");
        }
    }
}
