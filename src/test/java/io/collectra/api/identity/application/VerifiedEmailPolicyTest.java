package io.collectra.api.identity.application;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VerifiedEmailPolicyTest {
    private static final String COLLECTRA = "https://iam.example/realms/collectra";
    private static final String OTHER = "https://iam.example/realms/app-b";

    @Test
    void activationRequiresEmailAndVerification() {
        assertFalse(VerifiedEmailPolicy.canActivate(null, true));
        assertFalse(VerifiedEmailPolicy.canActivate("", true));
        assertFalse(VerifiedEmailPolicy.canActivate("person@example.com", false));
        assertFalse(VerifiedEmailPolicy.canActivate("invalid", true));
        assertTrue(VerifiedEmailPolicy.canActivate("person@example.com", true));
    }

    @Test
    void notificationRequiresCorrectRealmVerifiedEmailAndEnabledRecipient() {
        assertTrue(VerifiedEmailPolicy.canSend(COLLECTRA, COLLECTRA, "person@example.com", true, true));
        assertFalse(VerifiedEmailPolicy.canSend(COLLECTRA, OTHER, "person@example.com", true, true));
        assertFalse(VerifiedEmailPolicy.canSend(COLLECTRA, COLLECTRA, "person@example.com", false, true));
        assertFalse(VerifiedEmailPolicy.canSend(COLLECTRA, COLLECTRA, "person@example.com", true, false));
        assertFalse(VerifiedEmailPolicy.canSend(null, COLLECTRA, "person@example.com", true, true));
    }

    @Test
    void normalizesContactWithoutLinkingAcrossRealms() {
        assertEquals("person@example.com", VerifiedEmailPolicy.normalizedEmail(" Person@Example.COM "));
        assertThrows(IllegalArgumentException.class, () -> VerifiedEmailPolicy.normalizedEmail(null));
    }
}
