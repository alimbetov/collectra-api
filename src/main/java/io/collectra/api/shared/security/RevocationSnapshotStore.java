package io.collectra.api.shared.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Atomic, monotonic local state. A duplicate may refresh freshness only if identical. */
@Repository
public class RevocationSnapshotStore {
    private final JdbcClient db;
    public RevocationSnapshotStore(JdbcClient db) { this.db=db; }

    @Transactional
    public void apply(String issuer,String subject,long version,String state) {
        if(issuer==null || issuer.isBlank() || subject==null || subject.isBlank()
                || version<1 || !("ACTIVE".equals(state)||"BLOCKED".equals(state)))
            throw new IllegalArgumentException("Invalid revocation snapshot");
        int updated=db.sql("""
            INSERT INTO collectra_global_access_snapshots(issuer,subject_id,version,state,refreshed_at)
            VALUES (:iss,:sub,:v,:state,now())
            ON CONFLICT (issuer,subject_id) DO UPDATE
              SET version=EXCLUDED.version,state=EXCLUDED.state,refreshed_at=now()
            WHERE collectra_global_access_snapshots.version < EXCLUDED.version
               OR (collectra_global_access_snapshots.version=EXCLUDED.version
                   AND collectra_global_access_snapshots.state=EXCLUDED.state)
            """).param("iss",issuer).param("sub",subject).param("v",version)
            .param("state",state).update();
        if(updated!=1) throw new IllegalStateException("Stale or conflicting revocation snapshot");
    }
}
