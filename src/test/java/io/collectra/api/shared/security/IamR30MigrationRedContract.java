package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * IAM-R3.0A executable RED contracts.
 *
 * <p>Intentionally excluded from the normal Surefire *Test naming convention. Run explicitly:
 * mvn -Dtest=IamR30MigrationRedContract test
 *
 * <p>These tests specify the missing migration safety API. They are expected to fail until
 * R3.0B implements the contract; do not mark them PASS or silently disable their assertions.
 */
class IamR30MigrationRedContract {

    private static Class<?> requiredClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(
                    "IAM-R3.0B RED: missing production migration contract " + name, missing);
        }
    }

    @Test
    void explicitCredentialAuthorityModesMustExist() {
        Class<?> mode = requiredClass(
                "io.collectra.api.shared.security.CredentialAuthorityMode");
        assertThat(mode.isEnum()).as("CredentialAuthorityMode must be an enum").isTrue();
        assertThat(Arrays.stream(mode.getEnumConstants()).map(Object::toString))
                .contains("LEGACY", "MIGRATION", "KEYCLOAK_ONLY");
    }

    @Test
    void startupMustValidateCredentialAuthorityConfiguration() {
        Class<?> validator = requiredClass(
                "io.collectra.api.shared.security.CredentialAuthorityStartupValidator");
        assertThat(Arrays.stream(validator.getDeclaredMethods()).map(Method::getName))
                .as("Fail-closed startup validation must have an explicit entry point")
                .contains("validate");
    }

    @Test
    void migratedIdentitiesMustHaveExplicitLegacyCredentialDenialPolicy() {
        Class<?> policy = requiredClass(
                "io.collectra.api.shared.security.LegacyCredentialCutoverPolicy");
        assertThat(Arrays.stream(policy.getDeclaredMethods()).map(Method::getName))
                .as("Login, refresh and reset must consult a shared migrated-user denial policy")
                .contains("denyLegacyCredentialForMigratedIdentity");
    }
}
