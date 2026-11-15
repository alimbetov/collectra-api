package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * IAM-R3.0B: real PostgreSQL migration contracts, isolated from ordinary Surefire discovery.
 * Run: mvn -Dtest=IamR30bIdentityMigrationPostgresRed test
 *
 * The actual 054 migration SQL is executed against a minimal FK fixture. This is a focused
 * schema-contract test, NOT a substitute for full Liquibase migration regression.
 */
@Testcontainers
class IamR30bIdentityMigrationPostgresRed {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcClient db;
    private UUID localUser;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        db = JdbcClient.create(source);
        db.sql("DROP TABLE IF EXISTS collectra_identity_links CASCADE").update();
        db.sql("DROP TABLE IF EXISTS user_accounts CASCADE").update();
        db.sql("CREATE TABLE user_accounts (id UUID PRIMARY KEY)").update();
        try (InputStream stream = new ClassPathResource(
                "db/changelog/changes/054-keycloak-identity-links.sql").getInputStream()) {
            String migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            // The checked-in 054 changeset has plain SQL statements without procedural blocks.
            for (String statement : migration.replaceAll("(?m)^--.*$", "").split(";")) {
                if (!statement.isBlank()) db.sql(statement).update();
            }
        }
        localUser = UUID.randomUUID();
        db.sql("INSERT INTO user_accounts(id) VALUES (:id)")
                .param("id", localUser).update();
    }

    @Test
    void verifiedIdentityLinkPreservesExistingCollectraUuid() {
        String issuer = "https://id.example/realms/shared";
        String subject = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO collectra_identity_links
                    (issuer,subject_id,collectra_user_id,status,verified_at)
                VALUES (:iss,:sub,:uid,'VERIFIED',now())
                """)
                .param("iss", issuer).param("sub", subject).param("uid", localUser).update();
        assertThat(new VerifiedIdentityLinkRepository(db).verifiedLocalUser(issuer, subject))
                .contains(localUser);
    }

    @Test
    void differentKeycloakSubjectCannotStealExistingLocalUser() {
        String issuer = "https://id.example/realms/shared";
        db.sql("""
                INSERT INTO collectra_identity_links
                    (issuer,subject_id,collectra_user_id,status,verified_at)
                VALUES (:iss,'original',:uid,'VERIFIED',now())
                """)
                .param("iss", issuer).param("uid", localUser).update();
        assertThatThrownBy(() -> db.sql("""
                INSERT INTO collectra_identity_links
                    (issuer,subject_id,collectra_user_id,status,verified_at)
                VALUES (:iss,'attacker',:uid,'VERIFIED',now())
                """)
                .param("iss", issuer).param("uid", localUser).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(new VerifiedIdentityLinkRepository(db).verifiedLocalUser(issuer, "original"))
                .contains(localUser);
    }

    @Test
    void identityLinkMustRejectUnknownCollectraUser() {
        assertThatThrownBy(() -> db.sql("""
                INSERT INTO collectra_identity_links
                    (issuer,subject_id,collectra_user_id,status,verified_at)
                VALUES ('issuer','unknown',:uid,'VERIFIED',now())
                """)
                .param("uid", UUID.randomUUID()).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }


    @Test
    void sameGlobalSubjectCannotBeLinkedToTwoCollectraUsers() {
        UUID otherUser = UUID.randomUUID();
        db.sql("INSERT INTO user_accounts(id) VALUES (:id)")
                .param("id", otherUser).update();
        db.sql("""
                INSERT INTO collectra_identity_links
                    (issuer,subject_id,collectra_user_id,status,verified_at)
                VALUES ('issuer','shared-subject',:uid,'VERIFIED',now())
                """).param("uid", localUser).update();
        assertThatThrownBy(() -> db.sql("""
                INSERT INTO collectra_identity_links
                    (issuer,subject_id,collectra_user_id,status,verified_at)
                VALUES ('issuer','shared-subject',:uid,'VERIFIED',now())
                """).param("uid", otherUser).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(new VerifiedIdentityLinkRepository(db)
                .verifiedLocalUser("issuer", "shared-subject")).contains(localUser);
    }

    @Test
    void revokedLinkMustNotAuthenticateLocalUser() {
        db.sql("""
                INSERT INTO collectra_identity_links
                    (issuer,subject_id,collectra_user_id,status)
                VALUES ('issuer','revoked-subject',:uid,'REVOKED')
                """).param("uid", localUser).update();
        assertThat(new VerifiedIdentityLinkRepository(db)
                .verifiedLocalUser("issuer", "revoked-subject")).isEmpty();
    }

    @Test
    void verifiedLinkRequiresVerificationTimestamp() {
        assertThatThrownBy(() -> db.sql("""
                INSERT INTO collectra_identity_links
                    (issuer,subject_id,collectra_user_id,status)
                VALUES ('issuer','missing-proof',:uid,'VERIFIED')
                """).param("uid", localUser).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void migratedUserMustHaveDurableCredentialAuthorityStateRed() {
        // RED: current 054 schema stores verified identity link but does not yet persist
        // an explicit, enforceable credential authority cutover state.
        assertThat(db.sql("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name='user_accounts' AND column_name='credential_authority'
                """).query(Long.class).single())
                .as("R3.0B: credential authority must be persisted and enforced by login/refresh/reset")
                .isEqualTo(1L);
    }
}
