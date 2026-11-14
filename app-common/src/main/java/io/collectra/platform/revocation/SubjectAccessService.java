package io.collectra.platform.revocation;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubjectAccessService {
    private final JdbcClient db;
    public SubjectAccessService(JdbcClient db) { this.db = db; }

    @Transactional
    public Map<String,Object> block(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issuer and subject required");
        // PostgreSQL serializes competing updates for the same subject via row lock.
        db.sql("INSERT INTO subject_access_state(issuer,subject_id,state,version) " +
            "VALUES (:iss,:sub,'ACTIVE',1) ON CONFLICT (issuer,subject_id) DO NOTHING")
            .param("iss",issuer).param("sub",subject).update();
        long version = db.sql("UPDATE subject_access_state SET state='BLOCKED',version=version+1,updated_at=now() " +
            "WHERE issuer=:iss AND subject_id=:sub RETURNING version")
            .param("iss",issuer).param("sub",subject).query(Long.class).single();
        UUID operation = UUID.randomUUID();
        db.sql("INSERT INTO enforcement_operations(id,issuer,subject_id,target_version,state) " +
            "VALUES (:id,:iss,:sub,:v,'STATE_COMMITTED')")
            .param("id",operation).param("iss",issuer).param("sub",subject).param("v",version).update();
        db.sql("INSERT INTO access_state_outbox(id,issuer,subject_id,target_version,event_type) " +
            "VALUES (:id,:iss,:sub,:v,'SUBJECT_BLOCKED')")
            .param("id",UUID.randomUUID()).param("iss",issuer).param("sub",subject).param("v",version).update();
        return Map.of("operationId",operation,"state","STATE_COMMITTED","version",version);
    }

    // Local database read is not yet wired into every resource server. Never treat this
    // method alone as proof that an already-issued JWT is globally revoked.
    @Transactional(readOnly = true)
    public boolean isBlocked(String issuer, String subject) {
        return db.sql("SELECT state FROM subject_access_state WHERE issuer=:iss AND subject_id=:sub")
            .param("iss",issuer).param("sub",subject).query(String.class)
            .optional().map("BLOCKED"::equals).orElse(false);
    }
}
