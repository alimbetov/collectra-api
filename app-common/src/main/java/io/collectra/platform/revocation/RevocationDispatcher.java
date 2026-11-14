package io.collectra.platform.revocation;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Retryable Keycloak application; distributed consumer delivery is a separate gate. */
@Component
public class RevocationDispatcher {
    private final JdbcClient db;
    private final KeycloakAdminClient keycloak;
    private final boolean enabled;
    private final TransactionTemplate transactions;
    public RevocationDispatcher(JdbcClient db, KeycloakAdminClient keycloak,
        PlatformTransactionManager transactionManager,
        @Value("${app-common.revocation.dispatch-enabled:false}") boolean enabled) {
        this.db=db; this.keycloak=keycloak; this.enabled=enabled;
        this.transactions=new TransactionTemplate(transactionManager);
    }
    @Scheduled(fixedDelayString="${app-common.revocation.dispatch-delay-ms:5000}")
    public void dispatch() {
        if (!enabled) return;
        var events=db.sql("SELECT id,issuer,subject_id,target_version FROM access_state_outbox " +
            "WHERE published_at IS NULL AND next_attempt_at<=now() ORDER BY created_at LIMIT 20")
            .query((rs,n)->new Event(rs.getObject("id",UUID.class),rs.getString("issuer"),
                rs.getString("subject_id"),rs.getLong("target_version"))).list();
        for (var event:events) {
            // Claim row atomically; only one dispatcher owns this attempt.
            int claimed=db.sql("UPDATE access_state_outbox SET next_attempt_at=now()+interval '2 minutes', " +
                "attempts=attempts+1 WHERE id=:id AND published_at IS NULL AND next_attempt_at<=now()")
                .param("id",event.id()).update();
            if (claimed==0) continue;
            try {
                keycloak.disableAndLogout(event.issuer(),event.subject());
                // Keycloak is an external side effect: retry safely if the process crashes.
                // Persist its success and operation transition in ONE local transaction.
                transactions.executeWithoutResult(status -> {
                    db.sql("UPDATE access_state_outbox SET keycloak_applied_at=now(), published_at=now(), " +
                        "last_error=NULL WHERE id=:id AND published_at IS NULL")
                        .param("id",event.id()).update();
                    db.sql("UPDATE enforcement_operations SET state='PROPAGATING' " +
                        "WHERE issuer=:iss AND subject_id=:sub AND target_version=:v AND state='STATE_COMMITTED'")
                        .param("iss",event.issuer()).param("sub",event.subject())
                        .param("v",event.version()).update();
                });
            } catch (RuntimeException e) {
                db.sql("UPDATE access_state_outbox SET last_error=:error, " +
                    "next_attempt_at=now()+interval '2 minutes' WHERE id=:id")
                    .param("error",e.getClass().getSimpleName()).param("id",event.id()).update();
            }
        }
    }
    private record Event(UUID id,String issuer,String subject,long version) {}
}
