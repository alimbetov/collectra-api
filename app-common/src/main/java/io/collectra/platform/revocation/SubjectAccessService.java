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
        // Capture both required consumers under the same transaction as the operation.
        long mandatory=db.sql("SELECT count(*) FROM revocation_consumers " +
            "WHERE required_for_enforcement=true AND enabled=true AND oauth_client_id IS NOT NULL " +
            "AND consumer_id IN ('collectra-api','app-b-api')")
            .query(Long.class).single();
        long totalRequired=db.sql("SELECT count(*) FROM revocation_consumers WHERE required_for_enforcement=true")
            .query(Long.class).single();
        if(mandatory!=2 || totalRequired!=2)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Mandatory consumers not ready");
        // Unknown subjects require explicit privileged enrollment.
        long version = db.sql("UPDATE subject_access_state SET state='BLOCKED',version=version+1,updated_at=now() " +
            "WHERE issuer=:iss AND subject_id=:sub RETURNING version")
            .param("iss",issuer).param("sub",subject).query(Long.class).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Subject not enrolled"));
        UUID operation = UUID.randomUUID();
        db.sql("INSERT INTO enforcement_operations(id,issuer,subject_id,target_version,state) " +
            "VALUES (:id,:iss,:sub,:v,'STATE_COMMITTED')")
            .param("id",operation).param("iss",issuer).param("sub",subject).param("v",version).update();
        int captured=db.sql("INSERT INTO enforcement_operation_consumers(operation_id,consumer_id,oauth_client_id,binding_epoch) " +
            "SELECT :op,consumer_id,oauth_client_id,binding_epoch FROM revocation_consumers " +
            "WHERE required_for_enforcement=true AND enabled=true AND oauth_client_id IS NOT NULL")
            .param("op",operation).update();
        if(captured!=2) throw new IllegalStateException("Incomplete consumer obligation snapshot");
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
        // Fail closed for unregistered identities and on database errors.
        return db.sql("SELECT state FROM subject_access_state WHERE issuer=:iss AND subject_id=:sub")
            .param("iss",issuer).param("sub",subject).query(String.class)
            .optional().map("ACTIVE"::equals).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String issuer, String subject) {
        // Unknown is not ACTIVE and therefore cannot be authorized.
        return !isAllowed(issuer,subject);
    }

}
