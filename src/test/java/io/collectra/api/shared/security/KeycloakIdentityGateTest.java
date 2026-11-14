package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.collectra.api.identity.infrastructure.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KeycloakIdentityGateTest {
    private static final String ISS = "https://id.example/realms/shared";

    @Test
    void rejectsUnknownGlobalSubject() {
        var links = mock(VerifiedIdentityLinkRepository.class);
        var users = mock(UserAccountRepository.class);
        when(links.verifiedLocalUser(ISS, "unlinked")).thenReturn(Optional.empty());
        assertThat(new KeycloakIdentityGate(links, users, ISS).isVerifiedActive(ISS, "unlinked"))
                .isFalse();
        verifyNoInteractions(users);
    }

    @Test
    void rejectsWrongIssuerWithoutLinkLookup() {
        var links = mock(VerifiedIdentityLinkRepository.class);
        var users = mock(UserAccountRepository.class);
        assertThat(
                        new KeycloakIdentityGate(links, users, ISS)
                                .isVerifiedActive("https://attacker.example/realms/shared", "user"))
                .isFalse();
        verifyNoInteractions(links, users);
    }
}
