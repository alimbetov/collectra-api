package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL behavioral tests. Execute explicitly via -Dtest=RevocationSnapshotPostgresIT. */
@Testcontainers
class RevocationSnapshotPostgresIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    JdbcClient db;
    RevocationSnapshotStore store;
    GlobalRevocationGate gate;
    static final String ISS = "https://id.example/realms/shared", SUB = "subject-1";

    @BeforeEach
    void setup() {
        var source =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        db = JdbcClient.create(source);
        db.sql("DROP TABLE IF EXISTS collectra_global_access_snapshots").update();
        db.sql(
                        """
            CREATE TABLE collectra_global_access_snapshots (
             issuer VARCHAR(512) NOT NULL,subject_id VARCHAR(255) NOT NULL,
             version BIGINT NOT NULL CHECK(version>=0),
             state VARCHAR(16) NOT NULL CHECK(state IN ('ACTIVE','BLOCKED')),
             refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
             PRIMARY KEY(issuer,subject_id))
            """)
                .update();
        store = new RevocationSnapshotStore(db);
        gate = new GlobalRevocationGate(db, Duration.ofSeconds(30));
    }

    @Test
    void sameSubjectActiveThenBlockedDeniesPreviouslyIssuedToken() {
        assertThat(gate.allows(ISS, SUB)).isFalse();
        store.apply(ISS, SUB, 1, "ACTIVE");
        assertThat(gate.allows(ISS, SUB)).isTrue();
        store.apply(ISS, SUB, 2, "BLOCKED");
        assertThat(gate.allows(ISS, SUB)).isFalse();
    }

    @Test
    void duplicateAndOlderActiveNeverUndoBlock() {
        store.apply(ISS, SUB, 2, "BLOCKED");
        store.apply(ISS, SUB, 2, "BLOCKED");
        assertThatThrownBy(() -> store.apply(ISS, SUB, 1, "ACTIVE"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.apply(ISS, SUB, 2, "ACTIVE"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(gate.allows(ISS, SUB)).isFalse();
    }

    @Test
    void staleActiveFailsClosed() {
        store.apply(ISS, SUB, 1, "ACTIVE");
        db.sql(
                        "UPDATE collectra_global_access_snapshots SET refreshed_at=now()-interval '40 seconds'")
                .update();
        assertThat(gate.allows(ISS, SUB)).isFalse();
    }

    @Test
    void unavailableLedgerFailsClosedForPreviouslyActiveSubject() {
        store.apply(ISS, SUB, 1, "ACTIVE");
        assertThat(gate.allows(ISS, SUB)).isTrue();
        db.sql("DROP TABLE collectra_global_access_snapshots").update();
        assertThat(gate.allows(ISS, SUB)).isFalse();
    }

    @Test
    void differentSubjectCannotInheritState() {
        store.apply(ISS, SUB, 1, "ACTIVE");
        assertThat(gate.allows(ISS, "different-subject")).isFalse();
    }

    @Test
    void staleActiveRefreshUsesAuthoritativeStateBeforeAdmission() {
        store.apply(ISS, SUB, 1, "ACTIVE");
        db.sql(
                        "UPDATE collectra_global_access_snapshots "
                                + "SET refreshed_at=now()-interval '40 seconds'")
                .update();
        var remote = mock(RevocationSnapshotConsumer.class);
        doAnswer(
                        invocation -> {
                            store.apply(ISS, SUB, 2, "BLOCKED");
                            return null;
                        })
                .when(remote)
                .synchronize(SUB);
        var readThrough = new GlobalRevocationGate(db, remote, Duration.ofSeconds(30), 4);

        assertThat(readThrough.allows(ISS, SUB)).isFalse();
        verify(remote, times(1)).synchronize(SUB);
        assertThat(readThrough.allows(ISS, SUB)).isFalse();
        verifyNoMoreInteractions(remote);
    }

    @Test
    void staleActiveCanBeRefreshedWithoutFullPopulationScan() {
        store.apply(ISS, SUB, 1, "ACTIVE");
        db.sql(
                        "UPDATE collectra_global_access_snapshots "
                                + "SET refreshed_at=now()-interval '40 seconds'")
                .update();
        var remote = mock(RevocationSnapshotConsumer.class);
        doAnswer(
                        invocation -> {
                            store.apply(ISS, SUB, 1, "ACTIVE");
                            return null;
                        })
                .when(remote)
                .synchronize(SUB);
        var readThrough = new GlobalRevocationGate(db, remote, Duration.ofSeconds(30), 4);

        assertThat(readThrough.allows(ISS, SUB)).isTrue();
        assertThat(readThrough.allows(ISS, SUB)).isTrue();
        verify(remote, times(1)).synchronize(SUB);
    }

    @Test
    void remoteOutageNeverTurnsExpiredActiveSnapshotIntoAuthorization() {
        store.apply(ISS, SUB, 1, "ACTIVE");
        db.sql(
                        "UPDATE collectra_global_access_snapshots "
                                + "SET refreshed_at=now()-interval '40 seconds'")
                .update();
        var remote = mock(RevocationSnapshotConsumer.class);
        doThrow(new IllegalStateException("AC is down")).when(remote).synchronize(SUB);
        var readThrough = new GlobalRevocationGate(db, remote, Duration.ofSeconds(30), 4);

        assertThat(readThrough.allows(ISS, SUB)).isFalse();
        assertThat(gate.allows(ISS, SUB)).isFalse();
    }

    @Test
    void concurrentRequestsForOneStaleSubjectShareOneRefresh() throws Exception {
        store.apply(ISS, SUB, 1, "ACTIVE");
        db.sql(
                        "UPDATE collectra_global_access_snapshots "
                                + "SET refreshed_at=now()-interval '40 seconds'")
                .update();
        var remote = mock(RevocationSnapshotConsumer.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            started.countDown();
                            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            store.apply(ISS, SUB, 1, "ACTIVE");
                            return null;
                        })
                .when(remote)
                .synchronize(SUB);
        var readThrough = new GlobalRevocationGate(db, remote, Duration.ofSeconds(30), 2);
        var workers = Executors.newFixedThreadPool(8);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                tasks.add(workers.submit(() -> readThrough.allows(ISS, SUB)));
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (var task : tasks) {
                assertThat(task.get(10, TimeUnit.SECONDS)).isTrue();
            }
            verify(remote, times(1)).synchronize(SUB);
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void concurrentDistinctColdSubjectsFailClosedAtConfiguredCapacity() throws Exception {
        String secondSubject = "subject-2";
        store.apply(ISS, SUB, 1, "ACTIVE");
        store.apply(ISS, secondSubject, 1, "ACTIVE");
        db.sql(
                        "UPDATE collectra_global_access_snapshots "
                                + "SET refreshed_at=now()-interval '40 seconds'")
                .update();
        var remote = mock(RevocationSnapshotConsumer.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            String subject = invocation.getArgument(0);
                            if (SUB.equals(subject)) {
                                started.countDown();
                                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            }
                            store.apply(ISS, subject, 1, "ACTIVE");
                            return null;
                        })
                .when(remote)
                .synchronize(anyString());
        var readThrough = new GlobalRevocationGate(db, remote, Duration.ofSeconds(30), 1);
        var workers = Executors.newSingleThreadExecutor();
        try {
            var first = workers.submit(() -> readThrough.allows(ISS, SUB));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(readThrough.allows(ISS, secondSubject)).isFalse();
            verify(remote, never()).synchronize(secondSubject);

            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(readThrough.allows(ISS, secondSubject)).isTrue();
            verify(remote, times(1)).synchronize(secondSubject);
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }
}
