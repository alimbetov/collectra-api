package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.identity.domain.RefreshSession;
import io.collectra.api.identity.domain.SystemRole;
import io.collectra.api.identity.domain.TenantMembership;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.RefreshSessionRepository;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Full Liquibase PostgreSQL migration contract, not a Mockito or in-memory database test. Proves
 * durability, rollback, and serialized concurrent cutover for the same local identity.
 */
class CredentialAuthorityMigrationPostgresIT extends AbstractIntegrationTest {
    private static final String ISSUER = "https://id.example/realms/shared";

    @Autowired TenantRepository tenants;
    @Autowired UserAccountRepository users;
    @Autowired TenantMembershipRepository memberships;
    @Autowired RefreshSessionRepository sessions;
    @Autowired CredentialAuthorityMigrationService migration;
    @Autowired JdbcClient db;
    @Autowired PlatformTransactionManager txManager;

    private record Fixture(UUID userId, UUID sessionId, UUID membershipId) {}

    private Fixture create(boolean verified) {
        var tenant =
                tenants.saveAndFlush(
                        new Tenant("cutover-" + UUID.randomUUID(), "IAM A2 PostgreSQL"));
        var user =
                users.saveAndFlush(
                        new UserAccount(
                                tenant.getId(),
                                "cutover-" + UUID.randomUUID() + "@example.invalid",
                                "test-hash",
                                SystemRole.TENANT_USER));
        var membership =
                memberships.saveAndFlush(new TenantMembership(tenant.getId(), user.getId()));
        var session =
                sessions.saveAndFlush(
                        new RefreshSession(
                                user.getId(),
                                tenant.getId(),
                                membership.getId(),
                                UUID.randomUUID().toString().replace("-", ""),
                                UUID.randomUUID(),
                                Instant.now().plusSeconds(3600)));
        if (verified) {
            db.sql(
                            """
                    INSERT INTO collectra_identity_links
                        (issuer,subject_id,collectra_user_id,status,verified_at)
                    VALUES (:issuer,:subject,:user,'VERIFIED',now())
                    """)
                    .param("issuer", ISSUER)
                    .param("subject", UUID.randomUUID().toString())
                    .param("user", user.getId())
                    .update();
        }
        return new Fixture(user.getId(), session.getId(), membership.getId());
    }

    private String authority(UUID userId) {
        return db.sql("SELECT credential_authority FROM user_accounts WHERE id=:user")
                .param("user", userId)
                .query(String.class)
                .single();
    }

    private long version(UUID userId) {
        return db.sql("SELECT authorization_version FROM user_accounts WHERE id=:user")
                .param("user", userId)
                .query(Long.class)
                .single();
    }

    private boolean revoked(UUID sessionId) {
        return db.sql("SELECT revoked_at IS NOT NULL FROM refresh_sessions WHERE id=:id")
                .param("id", sessionId)
                .query(Boolean.class)
                .single();
    }

    @Test
    void verifiedCutoverPersistsAuthorityAndRevokesSessionWithoutChangingLocalReferences() {
        Fixture f = create(true);
        long before = version(f.userId());
        migration.migrateVerifiedUser(f.userId());
        assertThat(authority(f.userId())).isEqualTo("KEYCLOAK");
        assertThat(version(f.userId())).isEqualTo(before + 1);
        assertThat(revoked(f.sessionId())).isTrue();
        assertThat(memberships.findById(f.membershipId())).isPresent();
        assertThat(users.findById(f.userId())).isPresent();
        migration.migrateVerifiedUser(f.userId());
        assertThat(version(f.userId())).isEqualTo(before + 1);
    }

    @Test
    void withoutVerifiedLinkMigrationCannotMutateAuthorityOrSession() {
        Fixture f = create(false);
        long before = version(f.userId());
        assertThatThrownBy(() -> migration.migrateVerifiedUser(f.userId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(authority(f.userId())).isEqualTo("LEGACY");
        assertThat(version(f.userId())).isEqualTo(before);
        assertThat(revoked(f.sessionId())).isFalse();
    }

    @Test
    void enclosingTransactionRollbackRestoresAuthorityVersionAndRefreshSession() {
        Fixture f = create(true);
        long before = version(f.userId());
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(txManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    migration.migrateVerifiedUser(f.userId());
                                                    throw new IllegalStateException(
                                                            "force rollback after successful migration");
                                                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(authority(f.userId())).isEqualTo("LEGACY");
        assertThat(version(f.userId())).isEqualTo(before);
        assertThat(revoked(f.sessionId())).isFalse();
    }

    @Test
    void concurrentCutoverSerializesAndIncrementsVersionExactlyOnce() throws Exception {
        Fixture f = create(true);
        long before = version(f.userId());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first =
                    executor.submit(
                            () -> {
                                start.await();
                                migration.migrateVerifiedUser(f.userId());
                                return null;
                            });
            var second =
                    executor.submit(
                            () -> {
                                start.await();
                                migration.migrateVerifiedUser(f.userId());
                                return null;
                            });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
            assertThat(authority(f.userId())).isEqualTo("KEYCLOAK");
            assertThat(version(f.userId())).isEqualTo(before + 1);
            assertThat(revoked(f.sessionId())).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
}
