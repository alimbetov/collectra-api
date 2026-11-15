package io.collectra.api.shared.security;

import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.RefreshSessionRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator-side migration primitive; deliberately not exposed as a public HTTP endpoint.
 * Preserves local UUID and business foreign keys. Existing JWTs become invalid via
 * authorizationVersion increment, and refresh sessions are revoked in the same transaction.
 */
@Service
public class CredentialAuthorityMigrationService {
    private final UserAccountRepository users;
    private final VerifiedIdentityLinkRepository links;
    private final RefreshSessionRepository sessions;

    public CredentialAuthorityMigrationService(UserAccountRepository users,
            VerifiedIdentityLinkRepository links, RefreshSessionRepository sessions) {
        this.users = users;
        this.links = links;
        this.sessions = sessions;
    }

    @Transactional
    public void migrateVerifiedUser(UUID userId) {
        UserAccount user = users.findByIdForCredentialMigration(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown local user"));
        if (user.getCredentialAuthority() == UserAccount.CredentialAuthority.KEYCLOAK) {
            return;
        }
        if (!links.hasVerifiedLinkForLocalUser(userId)) {
            throw new IllegalStateException("Verified identity link required");
        }
        user.migrateToKeycloak();
        sessions.revokeAllByUserId(userId);
    }
}
