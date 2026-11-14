package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL behavioral tests. Execute explicitly via -Dtest=RevocationSnapshotPostgresIT. */
@Testcontainers
class RevocationSnapshotPostgresIT {
    @Container static final PostgreSQLContainer<?> postgres=
        new PostgreSQLContainer<>("postgres:16-alpine");
    JdbcClient db;
    RevocationSnapshotStore store;
    GlobalRevocationGate gate;
    static final String ISS="https://id.example/realms/shared", SUB="subject-1";

    @BeforeEach void setup() {
        var source=new DriverManagerDataSource(
            postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        db=JdbcClient.create(source);
        db.sql("DROP TABLE IF EXISTS collectra_global_access_snapshots").update();
        db.sql("""
            CREATE TABLE collectra_global_access_snapshots (
             issuer VARCHAR(512) NOT NULL,subject_id VARCHAR(255) NOT NULL,
             version BIGINT NOT NULL CHECK(version>=0),
             state VARCHAR(16) NOT NULL CHECK(state IN ('ACTIVE','BLOCKED')),
             refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
             PRIMARY KEY(issuer,subject_id))
            """).update();
        store=new RevocationSnapshotStore(db);
        gate=new GlobalRevocationGate(db,Duration.ofSeconds(30));
    }

    @Test void sameSubjectActiveThenBlockedDeniesPreviouslyIssuedToken() {
        assertThat(gate.allows(ISS,SUB)).isFalse();
        store.apply(ISS,SUB,1,"ACTIVE");
        assertThat(gate.allows(ISS,SUB)).isTrue();
        store.apply(ISS,SUB,2,"BLOCKED");
        assertThat(gate.allows(ISS,SUB)).isFalse();
    }

    @Test void duplicateAndOlderActiveNeverUndoBlock() {
        store.apply(ISS,SUB,2,"BLOCKED");
        store.apply(ISS,SUB,2,"BLOCKED");
        assertThatThrownBy(()->store.apply(ISS,SUB,1,"ACTIVE"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->store.apply(ISS,SUB,2,"ACTIVE"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(gate.allows(ISS,SUB)).isFalse();
    }

    @Test void staleActiveFailsClosed() {
        store.apply(ISS,SUB,1,"ACTIVE");
        db.sql("UPDATE collectra_global_access_snapshots SET refreshed_at=now()-interval '40 seconds'").update();
        assertThat(gate.allows(ISS,SUB)).isFalse();
    }

    @Test void differentSubjectCannotInheritState() {
        store.apply(ISS,SUB,1,"ACTIVE");
        assertThat(gate.allows(ISS,"different-subject")).isFalse();
    }
}
