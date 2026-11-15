package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.collectra.api.identity.domain.SystemRole;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.RefreshSessionRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialAuthorityMigrationServiceTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final VerifiedIdentityLinkRepository links = mock(VerifiedIdentityLinkRepository.class);
    private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    private final CredentialAuthorityMigrationService service =
            new CredentialAuthorityMigrationService(users, links, sessions);

    @Test void verifiedCutoverKeepsLocalUuidAndInvalidatesLegacyCredentials() {
        var user = new UserAccount(UUID.randomUUID(), "test@example.org", "hash", SystemRole.TENANT_USER);
        UUID id = user.getId();
        long version = user.getAuthorizationVersion();
        when(users.findByIdForCredentialMigration(id)).thenReturn(Optional.of(user));
        when(links.hasVerifiedLinkForLocalUser(id)).thenReturn(true);
        service.migrateVerifiedUser(id);
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getCredentialAuthority()).isEqualTo(UserAccount.CredentialAuthority.KEYCLOAK);
        assertThat(user.getAuthorizationVersion()).isEqualTo(version + 1);
        verify(sessions).revokeAllByUserId(id);
        service.migrateVerifiedUser(id);
        verify(sessions, times(1)).revokeAllByUserId(id);
        verify(users, times(2)).findByIdForCredentialMigration(id);
        verify(users, never()).findById(id);
    }

    @Test void unverifiedLinkNeverChangesCredentialAuthority() {
        var user = new UserAccount(UUID.randomUUID(), "test@example.org", "hash", SystemRole.TENANT_USER);
        when(users.findByIdForCredentialMigration(user.getId())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.migrateVerifiedUser(user.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(user.getCredentialAuthority()).isEqualTo(UserAccount.CredentialAuthority.LEGACY);
        verifyNoInteractions(sessions);
    }
}
