package io.collectra.platform.revocation;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import liquibase.command.CommandScope;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises real production Liquibase changelog and PostgreSQL transaction isolation. */
@Testcontainers
class RevocationLiquibaseConcurrencyIT {
    @Container static final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
    static DriverManagerDataSource ds;
    static JdbcClient db;
    static TransactionTemplate tx;

    @BeforeAll static void migrate() throws Exception {
        ds=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
        db=JdbcClient.create(ds);
        tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        new CommandScope("update")
            .addArgumentValue("url",pg.getJdbcUrl())
            .addArgumentValue("username",pg.getUsername())
            .addArgumentValue("password",pg.getPassword())
            .addArgumentValue("changelogFile","db/changelog/db.changelog-master.yaml")
            .execute();
    }

    @BeforeEach void consumers() {
        db.sql("UPDATE revocation_consumers SET enabled=true,oauth_client_id=CASE consumer_id " +
            "WHEN 'collectra-api' THEN 'collectra-test' ELSE 'appb-test' END " +
            "WHERE consumer_id IN ('collectra-api','app-b-api')").update();
    }

    @Test void concurrentReplayCreatesExactlyOneOperation() throws Exception {
        String subject="concurrent-"+UUID.randomUUID(),key="request-"+UUID.randomUUID();
        tx.executeWithoutResult(s->new SubjectEnrollmentService(db).initialize("issuer",subject));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try {
            List<Future<Map<String,Object>>> futures=new ArrayList<>();
            for(int i=0;i<2;i++) futures.add(pool.submit(()->{
                ready.countDown();start.await();
                return tx.execute(s->new SubjectAccessService(db).block("issuer",subject,"actor",key));
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Map<String,Object> first=futures.get(0).get(30,TimeUnit.SECONDS);
            Map<String,Object> second=futures.get(1).get(30,TimeUnit.SECONDS);
            assertThat(second.get("operationId")).isEqualTo(first.get("operationId"));
            assertThat(second.get("version")).isEqualTo(first.get("version"));
            assertThat(db.sql("SELECT count(*) FROM enforcement_operations WHERE issuer='issuer' AND subject_id=:sub")
                .param("sub",subject).query(Long.class).single()).isEqualTo(1L);
            assertThat(db.sql("SELECT count(*) FROM enforcement_operation_consumers WHERE operation_id=:id")
                .param("id",first.get("operationId")).query(Long.class).single()).isEqualTo(2L);
        } finally {pool.shutdownNow();}
    }

    @Test void rebindInvalidatesEarlierAcknowledgement() {
        String subject="epoch-"+UUID.randomUUID();
        tx.executeWithoutResult(s->new SubjectEnrollmentService(db).initialize("issuer",subject));
        Map<String,Object> result=tx.execute(s->new SubjectAccessService(db)
            .block("issuer",subject,"actor","request-"+UUID.randomUUID()));
        UUID operation=(UUID)result.get("operationId");
        db.sql("UPDATE enforcement_operations SET state='PROPAGATING' WHERE id=:id")
            .param("id",operation).update();
        db.sql("UPDATE access_state_outbox SET keycloak_applied_at=now() " +
            "WHERE issuer='issuer' AND subject_id=:sub").param("sub",subject).update();
        long v=(long)result.get("version");
        for(String client:List.of("collectra-api","app-b-api")) {
            db.sql("INSERT INTO revocation_acks(consumer_id,issuer,subject_id,version,binding_epoch) " +
                "SELECT consumer_id,'issuer',:sub,:v,binding_epoch FROM revocation_consumers WHERE consumer_id=:c")
                .param("sub",subject).param("v",v).param("c",client).update();
        }
        db.sql("UPDATE revocation_consumers SET oauth_client_id='appb-rotated' WHERE consumer_id='app-b-api'").update();
        new EnforcementReconciler(db,true).reconcile();
        assertThat(db.sql("SELECT state FROM enforcement_operations WHERE id=:id")
            .param("id",operation).query(String.class).single()).isEqualTo("PROPAGATING");
    }
}
