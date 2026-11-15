package io.collectra.api.identity.application;

import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.shared.security.CredentialAuthorityMigrationService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlled migration of an EXISTING Collectra user. No email-based auto-linking. Global identity
 * creation and SMTP delivery are remote operations; failures are deliberately not represented as
 * successful activation.
 */
@Service
public class KeycloakOnboardingService {
    private final JdbcClient db;
    private final UserAccountRepository users;
    private final KeycloakOnboardingClient keycloak;
    private final CredentialAuthorityMigrationService migration;
    private final String issuer;

    public KeycloakOnboardingService(
            JdbcClient db,
            UserAccountRepository users,
            KeycloakOnboardingClient keycloak,
            CredentialAuthorityMigrationService migration,
            @Value("${collectra.security.keycloak.issuer:}") String issuer) {
        this.db = db;
        this.users = users;
        this.keycloak = keycloak;
        this.migration = migration;
        this.issuer = issuer;
    }

    /**
     * Reserve a stable subject in PostgreSQL before contacting Keycloak. A retry uses the same
     * subject; a partial remote failure never creates a second local onboarding record.
     */
    public UUID registerExisting(UUID localUserId) {
        var user =
                users.findById(localUserId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (user.getCredentialAuthority()
                != io.collectra.api.identity.domain.UserAccount.CredentialAuthority.LEGACY)
            throw new IllegalStateException("Already migrated");
        if (issuer.isBlank()) throw new IllegalStateException("Keycloak issuer missing");
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
        UUID id = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        // ON CONFLICT is the concurrency arbiter, not a check-then-insert.
        db.sql(
                        """
                INSERT INTO collectra_keycloak_onboarding(id,local_user_id,subject_id,email)
                VALUES (:id,:local,:subject,:email)
                ON CONFLICT (local_user_id) DO NOTHING
                """)
                .param("id", id)
                .param("local", localUserId)
                .param("subject", subject)
                .param("email", email)
                .update();
        var existing =
                db.sql(
                                """
                SELECT id,subject_id,email,status FROM collectra_keycloak_onboarding
                WHERE local_user_id=:local
                """)
                        .param("local", localUserId)
                        .query(
                                (rs, n) ->
                                        new Reservation(
                                                (UUID) rs.getObject(1),
                                                (UUID) rs.getObject(2),
                                                rs.getString(3),
                                                rs.getString(4)))
                        .single();
        if (!existing.email().equals(email) || "VERIFIED".equals(existing.status()))
            throw new IllegalStateException("Onboarding state conflict");
        try {
            keycloak.createUnverified(existing.subject(), existing.email());
        } catch (org.springframework.web.client.HttpClientErrorException.Conflict conflict) {
            // Retry after a successful remote create: only accept the exact reserved
            // subject and email, never search by email to link an unrelated identity.
            if (!keycloak.matchesReservedIdentity(existing.subject(), existing.email()))
                throw new IllegalStateException("Reserved Keycloak subject conflict", conflict);
        }
        // Atomic conditional update prevents concurrent requests from flooding SMTP.
        // Reserve before the remote call: a crash cannot trigger an immediate mail storm.
        int reserved =
                db.sql(
                                """
                UPDATE collectra_keycloak_onboarding
                SET verification_requested_at = now()
                WHERE id = :id AND status = 'PENDING'
                  AND (verification_requested_at IS NULL
                       OR verification_requested_at <= now() - interval '5 minutes')
                """)
                        .param("id", existing.id())
                        .update();
        if (reserved == 1) {
            // A failed send is retried after the cooldown; no false activation occurs.
            keycloak.sendVerifyEmail(existing.subject());
        }
        return existing.id();
    }

    @Transactional
    public void activate(UUID onboardingId) {
        var row =
                db.sql(
                                "SELECT local_user_id,subject_id,email,status FROM collectra_keycloak_onboarding WHERE id=:id FOR UPDATE")
                        .param("id", onboardingId)
                        .query(
                                (rs, n) ->
                                        new Pending(
                                                (UUID) rs.getObject(1),
                                                (UUID) rs.getObject(2),
                                                rs.getString(3),
                                                rs.getString(4)))
                        .optional()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown onboarding"));
        if ("VERIFIED".equals(row.status())) return;
        if (!keycloak.verified(row.subject(), row.email()))
            throw new IllegalStateException("Keycloak email not verified");
        var local = users.findById(row.local()).orElseThrow();
        if (!local.getEmail().equalsIgnoreCase(row.email()))
            throw new IllegalStateException("Local email changed");
        db.sql(
                        "INSERT INTO collectra_identity_links(issuer,subject_id,collectra_user_id,status,verified_at) VALUES (:iss,:sub,:local,'VERIFIED',now())")
                .param("iss", issuer)
                .param("sub", row.subject().toString())
                .param("local", row.local())
                .update();
        migration.migrateVerifiedUser(row.local());
        db.sql(
                        "UPDATE collectra_keycloak_onboarding SET status='VERIFIED',verified_at=now() WHERE id=:id")
                .param("id", onboardingId)
                .update();
    }

    private record Pending(UUID local, UUID subject, String email, String status) {}

    private record Reservation(UUID id, UUID subject, String email, String status) {}
}
