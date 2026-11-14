package io.collectra.platform.revocation;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Explicit operator-controlled recovery for a pending operation after OAuth client rotation.
 * Not exposed as an HTTP endpoint until operator authorization and approval are implemented.
 */
@Service
public class ConsumerBindingRecoveryService {
    private final JdbcClient db;
    public ConsumerBindingRecoveryService(JdbcClient db) { this.db=db; }

    @Transactional
    public void rebind(UUID operationId,String consumerId,String actor,String reason) {
        if(operationId==null || consumerId==null || consumerId.isBlank() ||
                actor==null || actor.isBlank() || reason==null || reason.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Recovery requires actor and reason");
        // Serialize recovery against reconciliation and concurrent recovery.
        var operation=db.sql("SELECT state FROM enforcement_operations WHERE id=:id FOR UPDATE")
            .param("id",operationId).query(String.class).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Operation not found"));
        if(!"PROPAGATING".equals(operation) && !"STATE_COMMITTED".equals(operation))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Operation is not pending");
        var old=db.sql("SELECT oauth_client_id,binding_epoch FROM enforcement_operation_consumers " +
                "WHERE operation_id=:op AND consumer_id=:consumer FOR UPDATE")
            .param("op",operationId).param("consumer",consumerId)
            .query((rs,n)->new Binding(rs.getString(1),rs.getLong(2))).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Consumer obligation missing"));
        var current=db.sql("SELECT oauth_client_id,binding_epoch FROM revocation_consumers " +
                "WHERE consumer_id=:consumer AND enabled=true AND required_for_enforcement=true " +
                "AND oauth_client_id IS NOT NULL FOR SHARE")
            .param("consumer",consumerId)
            .query((rs,n)->new Binding(rs.getString(1),rs.getLong(2))).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Consumer not ready"));
        if(current.epoch()<=old.get().epoch())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Recovery requires a newer binding epoch");
        db.sql("UPDATE enforcement_operation_consumers SET oauth_client_id=:client,binding_epoch=:epoch " +
                "WHERE operation_id=:op AND consumer_id=:consumer")
            .param("client",current.client()).param("epoch",current.epoch())
            .param("op",operationId).param("consumer",consumerId).update();
        db.sql("INSERT INTO enforcement_consumer_rebindings(id,operation_id,consumer_id," +
                "old_oauth_client_id,old_binding_epoch,new_oauth_client_id,new_binding_epoch,actor,reason) " +
                "VALUES (:id,:op,:consumer,:oldClient,:oldEpoch,:newClient,:newEpoch,:actor,:reason)")
            .param("id",UUID.randomUUID()).param("op",operationId).param("consumer",consumerId)
            .param("oldClient",old.get().client()).param("oldEpoch",old.get().epoch())
            .param("newClient",current.client()).param("newEpoch",current.epoch())
            .param("actor",actor).param("reason",reason).update();
    }
    private record Binding(String client,long epoch) {}
}
