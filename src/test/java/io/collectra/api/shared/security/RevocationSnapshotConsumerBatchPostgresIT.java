package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.identity.domain.SystemRole;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Full Liquibase PostgreSQL plus real localhost HTTP fixture; not an actual App Common runtime.
 * Tests the bounded scheduling behavior and read-through on-demand authorization.
 */
class RevocationSnapshotConsumerBatchPostgresIT extends AbstractIntegrationTest {
    private final String issuer = "https://id.example/realms/rev-batch-" + UUID.randomUUID();

    @Autowired JdbcClient db;
    @Autowired RevocationSnapshotStore snapshots;
    @Autowired TenantRepository tenants;
    @Autowired UserAccountRepository users;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startRemote(AtomicInteger acknowledgements) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/token",
                exchange -> {
                    byte[] body =
                            "{\"access_token\":\"test-machine-token\"}"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        server.createContext(
                "/api/v1/internal/revocations",
                exchange -> {
                    if (exchange.getRequestURI().getPath().endsWith("/ack")) {
                        acknowledgements.incrementAndGet();
                        exchange.sendResponseHeaders(202, -1);
                        exchange.close();
                        return;
                    }
                    byte[] body =
                            "{\"state\":\"ACTIVE\",\"version\":1}"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private ArrayList<String> enroll(int count) {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("rev-batch-" + UUID.randomUUID(), "Revocation batch test"));
        var subjects = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            UserAccount user =
                    users.saveAndFlush(
                            new UserAccount(
                                    tenant.getId(),
                                    "rev-batch-" + UUID.randomUUID() + "@example.invalid",
                                    "test-hash",
                                    SystemRole.TENANT_USER));
            String subject = "batch-" + UUID.randomUUID();
            subjects.add(subject);
            db.sql(
                            """
                            INSERT INTO collectra_identity_links
                                (issuer, subject_id, collectra_user_id, status, verified_at)
                            VALUES (:issuer, :subject, :user, 'VERIFIED', now())
                            """)
                    .param("issuer", issuer)
                    .param("subject", subject)
                    .param("user", user.getId())
                    .update();
        }
        return subjects;
    }

    @Test
    void boundedBackgroundScanAdvancesCursorAndWrapsAfterLastPage() throws Exception {
        var acknowledgements = new AtomicInteger();
        String base = startRemote(acknowledgements);
        var subjects = enroll(52);
        var consumer =
                new RevocationSnapshotConsumer(
                        db, snapshots, issuer, base, base + "/token", "machine", "secret", true);

        consumer.reconcile();
        assertThat(acknowledgements.get()).isEqualTo(50);
        consumer.reconcile();
        assertThat(acknowledgements.get()).isEqualTo(52);
        consumer.reconcile();
        assertThat(acknowledgements.get()).isEqualTo(52);
        consumer.reconcile();
        assertThat(acknowledgements.get()).isEqualTo(102);

        for (String subject : subjects) {
            assertThat(db.sql(
                            "SELECT version FROM collectra_global_access_snapshots "
                                    + "WHERE issuer=:issuer AND subject_id=:subject")
                            .param("issuer", issuer)
                            .param("subject", subject)
                            .query(Long.class)
                            .single())
                    .isEqualTo(1L);
        }
    }

    @Test
    void identityOutsideFirstBackgroundPageRefreshesOnDemand() throws Exception {
        var acknowledgements = new AtomicInteger();
        String base = startRemote(acknowledgements);
        var subjects = enroll(52);
        subjects.sort(String::compareTo);
        String lastSubject = subjects.get(subjects.size() - 1);
        var consumer =
                new RevocationSnapshotConsumer(
                        db, snapshots, issuer, base, base + "/token", "machine", "secret", true);
        consumer.reconcile();
        assertThat(acknowledgements.get()).isEqualTo(50);

        var gate = new GlobalRevocationGate(db, consumer, Duration.ofSeconds(30), 4);
        assertThat(gate.allows(issuer, lastSubject)).isTrue();
        assertThat(acknowledgements.get()).isEqualTo(51);
        assertThat(gate.allows(issuer, lastSubject)).isTrue();
        assertThat(acknowledgements.get()).isEqualTo(51);
    }
}
