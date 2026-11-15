package io.collectra.api.shared.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Never infer a link from email; links must be created by separately verified onboarding. */
@Repository
public class VerifiedIdentityLinkRepository {
    private final JdbcClient db;

    public VerifiedIdentityLinkRepository(JdbcClient db) {
        this.db = db;
    }

    public boolean hasVerifiedLinkForLocalUser(UUID userId) {
        return db.sql("SELECT EXISTS (SELECT 1 FROM collectra_identity_links WHERE collectra_user_id=:id AND status='VERIFIED')")
                .param("id", userId).query(Boolean.class).single();
    }

    public Optional<UUID> verifiedLocalUser(String issuer, String subject) {
        if (issuer == null || subject == null) return Optional.empty();
        return db.sql(
                        "SELECT collectra_user_id FROM collectra_identity_links "
                                + "WHERE issuer=:iss AND subject_id=:sub AND status='VERIFIED'")
                .param("iss", issuer)
                .param("sub", subject)
                .query(UUID.class)
                .optional();
    }
}
