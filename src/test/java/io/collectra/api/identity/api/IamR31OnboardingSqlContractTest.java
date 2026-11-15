package io.collectra.api.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Structural guard only; PostgreSQL behavioral qualification remains mandatory. */
class IamR31OnboardingSqlContractTest {
    private String resource(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void durableResendTimestampAndPendingIndexAreMigrated() throws Exception {
        String sql = resource("/db/changelog/changes/058-onboarding-email-cooldown.sql");
        assertThat(sql)
                .contains("verification_requested_at TIMESTAMPTZ")
                .contains("WHERE status = 'PENDING'");
    }

    @Test
    void masterIncludesCooldownMigration() throws Exception {
        assertThat(resource("/db/changelog/db.changelog-master.yaml"))
                .contains("058-onboarding-email-cooldown.sql");
    }

    @Test
    void uniqueReservationAndVerifiedLinkConstraintsExist() throws Exception {
        assertThat(resource("/db/changelog/changes/057-keycloak-onboarding.sql"))
                .contains("UNIQUE (local_user_id)")
                .contains("subject_id UUID NOT NULL UNIQUE");
        assertThat(resource("/db/changelog/changes/054-keycloak-identity-links.sql"))
                .contains("UNIQUE (collectra_user_id)");
    }
}
