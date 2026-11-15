package io.collectra.platform.revocation;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.server.ResponseStatusException;

/** Real PostgreSQL transaction/ACK gate tests; production migrations must be tested separately. */
@Testcontainers
class RevocationConsistencyPostgresIT {
    @Container static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
    JdbcClient db;
    @BeforeEach void setup() {
        db=JdbcClient.create(new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()));
        db.sql("DROP TABLE IF EXISTS enforcement_operation_consumers, revocation_acks, revocation_consumers, access_state_outbox, enforcement_operations, subject_access_state CASCADE").update();
        db.sql("""
            CREATE TABLE subject_access_state(issuer text NOT NULL,subject_id text NOT NULL,
            state text NOT NULL,version bigint NOT NULL,updated_at timestamptz DEFAULT now(),
            PRIMARY KEY(issuer,subject_id));
            CREATE TABLE enforcement_operations(id uuid PRIMARY KEY,issuer text NOT NULL,subject_id text NOT NULL,
            target_version bigint NOT NULL,state text NOT NULL);
            CREATE TABLE access_state_outbox(id uuid PRIMARY KEY,issuer text NOT NULL,subject_id text NOT NULL,
            target_version bigint NOT NULL,keycloak_applied_at timestamptz);
            CREATE TABLE revocation_consumers(consumer_id text PRIMARY KEY,enabled boolean NOT NULL,
            required_for_enforcement boolean NOT NULL,oauth_client_id text,binding_epoch bigint NOT NULL DEFAULT 1);
            CREATE TABLE enforcement_operation_consumers(operation_id uuid NOT NULL,consumer_id text NOT NULL,oauth_client_id text NOT NULL,binding_epoch bigint NOT NULL DEFAULT 1,PRIMARY KEY(operation_id,consumer_id));
            CREATE TABLE revocation_acks(consumer_id text NOT NULL,issuer text NOT NULL,
            subject_id text NOT NULL,version bigint NOT NULL,binding_epoch bigint NOT NULL DEFAULT 1,PRIMARY KEY(consumer_id,issuer,subject_id));
            """).update();
        db.sql("INSERT INTO revocation_consumers(consumer_id,enabled,required_for_enforcement,oauth_client_id) VALUES ('collectra-api',true,true,'collectra-consumer'),('app-b-api',true,true,'appb-consumer')").update();
    }
    @Test void bothMandatoryAcksRequiredForEnforced() {
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO enforcement_operations VALUES (:id,'issuer','sub',2,'PROPAGATING')")
            .param("id",id).update();
        db.sql("INSERT INTO access_state_outbox VALUES (:id,'issuer','sub',2,now())")
            .param("id",UUID.randomUUID()).update();
        snapshot(id);
        var reconciler=new EnforcementReconciler(db,true);
        reconciler.reconcile();
        assertState(id,"PROPAGATING");
        db.sql("INSERT INTO revocation_acks(consumer_id,issuer,subject_id,version) VALUES ('collectra-api','issuer','sub',2)").update();
        reconciler.reconcile();
        assertState(id,"PROPAGATING");
        db.sql("INSERT INTO revocation_acks VALUES ('app-b-api','issuer','sub',1)").update();
        reconciler.reconcile();
        assertState(id,"PROPAGATING");
        db.sql("UPDATE revocation_acks SET version=2 WHERE consumer_id='app-b-api'").update();
        reconciler.reconcile();
        assertState(id,"ENFORCED");
    }
    @Test void disabledAppBMustNotCountAsComplete() {
        db.sql("UPDATE revocation_consumers SET enabled=false WHERE consumer_id='app-b-api'").update();
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO enforcement_operations VALUES (:id,'issuer','sub',2,'PROPAGATING')")
            .param("id",id).update();
        db.sql("INSERT INTO access_state_outbox VALUES (:id,'issuer','sub',2,now())")
            .param("id",UUID.randomUUID()).update();
        db.sql("INSERT INTO revocation_acks VALUES ('collectra-api','issuer','sub',2)").update();
        snapshot(id);
        new EnforcementReconciler(db,true).reconcile();
        assertState(id,"PROPAGATING");
    }
    @Test void initializeIsIdempotentAndDoesNotUndoBlocked() {
        var service=new SubjectEnrollmentService(db);
        assertThat(service.initialize("issuer","sub").get("state")).isEqualTo("ACTIVE");
        db.sql("UPDATE subject_access_state SET state='BLOCKED',version=2 WHERE issuer='issuer' AND subject_id='sub'").update();
        assertThat(service.initialize("issuer","sub").get("state")).isEqualTo("BLOCKED");
        assertThat(service.initialize("issuer","sub").get("version")).isEqualTo(2L);
    }
    @Test void consumerRegistryMutationDoesNotRewriteOperationObligations() {
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO enforcement_operations VALUES (:id,'issuer','sub',2,'PROPAGATING')")
            .param("id",id).update();
        db.sql("INSERT INTO access_state_outbox VALUES (:id,'issuer','sub',2,now())")
            .param("id",UUID.randomUUID()).update();
        snapshot(id);
        db.sql("UPDATE revocation_consumers SET required_for_enforcement=false WHERE consumer_id='app-b-api'").update();
        db.sql("INSERT INTO revocation_acks VALUES ('collectra-api','issuer','sub',2)").update();
        new EnforcementReconciler(db,true).reconcile();
        assertState(id,"PROPAGATING");
    }
    @Test void staleAckBindingEpochCannotEnforce() {
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO enforcement_operations VALUES (:id,'issuer','sub',2,'PROPAGATING')")
            .param("id",id).update();
        db.sql("INSERT INTO access_state_outbox VALUES (:id,'issuer','sub',2,now())")
            .param("id",UUID.randomUUID()).update();
        snapshot(id);
        db.sql("INSERT INTO revocation_acks(consumer_id,issuer,subject_id,version,binding_epoch) VALUES " +
            "('collectra-api','issuer','sub',2,1),('app-b-api','issuer','sub',2,0)").update();
        new EnforcementReconciler(db,true).reconcile();
        assertState(id,"PROPAGATING");
    }
    @Test void thirdMandatoryConsumerRequiresItsOwnAck() {
        db.sql("INSERT INTO revocation_consumers(consumer_id,enabled,required_for_enforcement,oauth_client_id) VALUES ('zakaz-api',true,true,'zakaz-consumer')").update();
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO enforcement_operations VALUES (:id,'issuer','sub',2,'PROPAGATING')")
            .param("id",id).update();
        db.sql("INSERT INTO access_state_outbox VALUES (:id,'issuer','sub',2,now())")
            .param("id",UUID.randomUUID()).update();
        db.sql("INSERT INTO enforcement_operation_consumers(operation_id,consumer_id,oauth_client_id,binding_epoch) SELECT :id,consumer_id,oauth_client_id,binding_epoch FROM revocation_consumers WHERE required_for_enforcement=true")
            .param("id",id).update();
        db.sql("INSERT INTO revocation_acks(consumer_id,issuer,subject_id,version,binding_epoch) VALUES ('collectra-api','issuer','sub',2,1),('app-b-api','issuer','sub',2,1)").update();
        var reconciler=new EnforcementReconciler(db,true);
        reconciler.reconcile();
        assertState(id,"PROPAGATING");
        db.sql("INSERT INTO revocation_acks VALUES ('zakaz-api','issuer','sub',2,1)").update();
        reconciler.reconcile();
        assertState(id,"ENFORCED");
    }
    @Test void emptyOperationSnapshotNeverEnforces() {
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO enforcement_operations VALUES (:id,'issuer','sub',2,'PROPAGATING')")
            .param("id",id).update();
        db.sql("INSERT INTO access_state_outbox VALUES (:id,'issuer','sub',2,now())")
            .param("id",UUID.randomUUID()).update();
        new EnforcementReconciler(db,true).reconcile();
        assertState(id,"PROPAGATING");
    }
    @Test void newRegistryConsumerDoesNotRetroactivelyRewriteSnapshot() {
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO enforcement_operations VALUES (:id,'issuer','sub',2,'PROPAGATING')")
            .param("id",id).update();
        db.sql("INSERT INTO access_state_outbox VALUES (:id,'issuer','sub',2,now())")
            .param("id",UUID.randomUUID()).update();
        snapshot(id);
        db.sql("INSERT INTO revocation_consumers(consumer_id,enabled,required_for_enforcement,oauth_client_id) VALUES ('zakaz-api',true,true,'zakaz-consumer')").update();
        db.sql("INSERT INTO revocation_acks(consumer_id,issuer,subject_id,version,binding_epoch) VALUES ('collectra-api','issuer','sub',2,1),('app-b-api','issuer','sub',2,1)").update();
        new EnforcementReconciler(db,true).reconcile();
        assertState(id,"ENFORCED");
    }
    private void snapshot(UUID id) {
        db.sql("INSERT INTO enforcement_operation_consumers(operation_id,consumer_id,oauth_client_id) VALUES (:id,'collectra-api','collectra-consumer'),(:id,'app-b-api','appb-consumer')")
            .param("id",id).update();
    }
    private void assertState(UUID id,String expected) {
        assertThat(db.sql("SELECT state FROM enforcement_operations WHERE id=:id")
            .param("id",id).query(String.class).single()).isEqualTo(expected);
    }
}
