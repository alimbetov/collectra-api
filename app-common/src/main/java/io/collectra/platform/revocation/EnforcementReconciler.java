package io.collectra.platform.revocation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Disabled until all mandatory consumers are registered and verified. */
@Component
public class EnforcementReconciler {
    private final JdbcClient db;
    private final boolean enabled;
    public EnforcementReconciler(JdbcClient db,
        @Value("${app-common.revocation.reconcile-enabled:false}") boolean enabled) {
        this.db=db;this.enabled=enabled;
    }
    @Scheduled(fixedDelayString="${app-common.revocation.reconcile-delay-ms:5000}")
    public void reconcile() {
        if(!enabled) return;
        db.sql("""
            UPDATE enforcement_operations o SET state='ENFORCED'
            WHERE o.state='PROPAGATING'
              AND EXISTS (SELECT 1 FROM access_state_outbox x
                WHERE x.issuer=o.issuer AND x.subject_id=o.subject_id
                  AND x.target_version=o.target_version AND x.keycloak_applied_at IS NOT NULL)
              AND (SELECT count(*) FROM revocation_consumers c WHERE c.required_for_enforcement=true AND c.enabled=true AND c.oauth_client_id IS NOT NULL) = 2
              AND EXISTS (SELECT 1 FROM revocation_consumers c WHERE c.consumer_id='collectra-api' AND c.required_for_enforcement=true AND c.enabled=true AND c.oauth_client_id IS NOT NULL)
              AND EXISTS (SELECT 1 FROM revocation_consumers c WHERE c.consumer_id='app-b-api' AND c.required_for_enforcement=true AND c.enabled=true AND c.oauth_client_id IS NOT NULL)
              AND NOT EXISTS (
                SELECT 1 FROM revocation_consumers c
                WHERE c.required_for_enforcement=true AND NOT EXISTS (
                  SELECT 1 FROM revocation_acks a
                  WHERE a.consumer_id=c.consumer_id AND a.issuer=o.issuer
                    AND a.subject_id=o.subject_id AND a.version>=o.target_version
                )
              )
            """).update();
    }
}
