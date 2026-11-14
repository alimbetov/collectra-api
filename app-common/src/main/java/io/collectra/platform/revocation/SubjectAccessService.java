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
    public Map<String,Object> block(String issuer, String subject, String actor, String idempotencyKey) {
        if(actor==null || actor.isBlank() || idempotencyKey==null ||
                !idempotencyKey.matches("[A-Za-z0-9._:-]{8,160}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid Idempotency-Key required");
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issuer and subject required");
        // Serialize identical keys, including concurrent first requests, inside one transaction.
        db.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) locked")
            .param("key",issuer+"|"+actor+"|"+idempotencyKey).query(Long.class).single();
        var existing=db.sql("SELECT request_subject,operation_id,target_version FROM subject_block_idempotency " +
            "WHERE issuer=:iss AND actor_subject=:actor AND idempotency_key=:key")
            .param("iss",issuer).param("actor",actor).param("key",idempotencyKey)
            .query((rs,n)->new IdempotentBlock(rs.getString(1),rs.getObject(2,UUID.class),rs.getLong(3)))
            .optional();
        if(existing.isPresent()) {
            var previous=existing.get();
            if(!previous.subject().equals(subject))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused for another subject");
            return Map.of("operationId",previous.operation(),"state","STATE_COMMITTED","version",previous.version());
        }
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issuer and subject required");
        // Unknown subjects require explicit privileged enrollment.
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
        db.sql("INSERT INTO subject_block_idempotency(issuer,actor_subject,idempotency_key," +
            "request_subject,operation_id,target_version) VALUES (:iss,:actor,:key,:sub,:op,:v)")
            .param("iss",issuer).param("actor",actor).param("key",idempotencyKey)
            .param("sub",subject).param("op",operation).param("v",version).update();
        return Map.of("operationId",operation,"state","STATE_COMMITTED","version",version);
    }

    private record IdempotentBlock(String subject, UUID operation, long version) {}

    @Transactional(readOnly = true)
    public boolean isAllowed(String issuer, String subject) {
        // No ledger row means not explicitly blocked. DB failures propagate to the validator.
        return !isBlocked(issuer, subject);
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
