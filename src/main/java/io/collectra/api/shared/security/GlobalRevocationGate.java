package io.collectra.api.shared.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Strictly fail closed on missing, BLOCKED, or stale global access snapshot. */
@Component
public class GlobalRevocationGate {
    private final JdbcClient db;
    private final Duration maxAge;
    public GlobalRevocationGate(JdbcClient db,
            @Value("${collectra.security.keycloak.revocation-max-age:PT30S}") Duration maxAge) {
        this.db=db;this.maxAge=maxAge;
    }
    public boolean allows(String issuer,String subject) {
        if(issuer==null || subject==null || maxAge.isNegative() || maxAge.isZero()) return false;
        return db.sql("SELECT EXISTS (SELECT 1 FROM collectra_global_access_snapshots " +
                "WHERE issuer=:iss AND subject_id=:sub AND state='ACTIVE' " +
                "AND refreshed_at >= now() - (:max_age * interval '1 second'))")
            .param("iss",issuer).param("sub",subject)
            .param("max_age",maxAge.toMillis()/1000.0)
            .query(Boolean.class).single();
    }
}
