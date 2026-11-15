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
 * Controlled migration of an EXISTING Collectra user. No email-based auto-linking.
 * Global identity creation and SMTP delivery are remote operations; failures are
 * deliberately not represented as successful activation.
 */
@Service
public class KeycloakOnboardingService {
    private final JdbcClient db;
    private final UserAccountRepository users;
    private final KeycloakOnboardingClient keycloak;
    private final CredentialAuthorityMigrationService migration;
    private final String issuer;

    public KeycloakOnboardingService(JdbcClient db, UserAccountRepository users,
            KeycloakOnboardingClient keycloak, CredentialAuthorityMigrationService migration,
            @Value("${collectra.security.keycloak.issuer:}") String issuer) {
        this.db=db; this.users=users; this.keycloak=keycloak; this.migration=migration; this.issuer=issuer;
    }

    public UUID registerExisting(UUID localUserId) {
        var user=users.findById(localUserId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (user.getCredentialAuthority() != io.collectra.api.identity.domain.UserAccount.CredentialAuthority.LEGACY)
            throw new IllegalStateException("Already migrated");
        if (db.sql("SELECT EXISTS(SELECT 1 FROM collectra_keycloak_onboarding WHERE local_user_id=:id)")
                .param("id",localUserId).query(Boolean.class).single())
            throw new IllegalStateException("Onboarding already exists");
        if (issuer.isBlank()) throw new IllegalStateException("Keycloak issuer missing");
        String email=user.getEmail().trim().toLowerCase(Locale.ROOT);
        UUID subject=keycloak.createUnverified(email);
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO collectra_keycloak_onboarding(id,local_user_id,subject_id,email) VALUES (:id,:local,:subject,:email)")
                .param("id",id).param("local",localUserId).param("subject",subject).param("email",email).update();
        keycloak.sendVerifyEmail(subject);
        return id;
    }

    @Transactional
    public void activate(UUID onboardingId) {
        var row=db.sql("SELECT local_user_id,subject_id,email,status FROM collectra_keycloak_onboarding WHERE id=:id FOR UPDATE")
                .param("id",onboardingId)
                .query((rs,n)->new Pending((UUID)rs.getObject(1),(UUID)rs.getObject(2),rs.getString(3),rs.getString(4)))
                .optional().orElseThrow(() -> new IllegalArgumentException("Unknown onboarding"));
        if ("VERIFIED".equals(row.status())) return;
        if (!keycloak.verified(row.subject(),row.email())) throw new IllegalStateException("Keycloak email not verified");
        var local=users.findById(row.local()).orElseThrow();
        if (!local.getEmail().equalsIgnoreCase(row.email())) throw new IllegalStateException("Local email changed");
        db.sql("INSERT INTO collectra_identity_links(issuer,subject_id,collectra_user_id,status,verified_at) VALUES (:iss,:sub,:local,'VERIFIED',now())")
                .param("iss",issuer).param("sub",row.subject().toString()).param("local",row.local()).update();
        migration.migrateVerifiedUser(row.local());
        db.sql("UPDATE collectra_keycloak_onboarding SET status='VERIFIED',verified_at=now() WHERE id=:id")
                .param("id",onboardingId).update();
    }
    private record Pending(UUID local,UUID subject,String email,String status) {}
}
