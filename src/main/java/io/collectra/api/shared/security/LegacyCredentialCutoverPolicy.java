package io.collectra.api.shared.security;

import io.collectra.api.identity.domain.UserAccount;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

/** Server-side invariant: migrated identities can never mint local credentials. */
@Component
public class LegacyCredentialCutoverPolicy {
    public void denyLegacyCredentialForMigratedIdentity(UserAccount user) {
        if (user.getCredentialAuthority() == UserAccount.CredentialAuthority.KEYCLOAK) {
            throw new BadCredentialsException("Legacy credentials are disabled");
        }
    }
}
