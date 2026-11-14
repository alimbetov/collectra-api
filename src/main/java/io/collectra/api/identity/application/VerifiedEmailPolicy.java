package io.collectra.api.identity.application;

import java.util.Locale;

/**
 * Policy for application-scoped notification recipients. Keycloak owns email verification;
 * callers must supply a verified claim from the trusted application's issuer.
 */
public final class VerifiedEmailPolicy {
    private VerifiedEmailPolicy() {}

    public static boolean canActivate(String email, boolean emailVerified) {
        return emailVerified && isPresent(email);
    }

    public static boolean canSend(
            String expectedIssuer,
            String actualIssuer,
            String email,
            boolean emailVerified,
            boolean recipientEnabled) {
        return expectedIssuer != null
                && !expectedIssuer.isBlank()
                && expectedIssuer.equals(actualIssuer)
                && recipientEnabled
                && canActivate(email, emailVerified);
    }

    public static String normalizedEmail(String email) {
        if (!isPresent(email)) {
            throw new IllegalArgumentException("A valid email is required");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean isPresent(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String value = email.strip();
        int at = value.indexOf('@');
        return at > 0
                && at == value.lastIndexOf('@')
                && at < value.length() - 1
                && !value.contains(" ")
                && !value.contains("\t")
                && !value.contains("\n");
    }
}
