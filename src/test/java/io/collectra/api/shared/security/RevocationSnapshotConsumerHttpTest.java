package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real HTTP pull/ACK ordering, without pretending the HTTP fixture is a running AC service. */
class RevocationSnapshotConsumerHttpTest {
    HttpServer server;
    AtomicInteger acknowledgements;
    RevocationSnapshotStore store;
    RevocationSnapshotConsumer consumer;

    @BeforeEach
    void start() throws Exception {
        acknowledgements = new AtomicInteger();
        store = mock(RevocationSnapshotStore.class);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            byte[] body = "{\"access_token\":\"machine-test-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/api/v1/internal/revocations", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/ack")) {
                acknowledgements.incrementAndGet();
                exchange.sendResponseHeaders(202, -1);
            } else {
                byte[] body = "{\"state\":\"BLOCKED\",\"version\":2}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            }
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        consumer = new RevocationSnapshotConsumer(mock(JdbcClient.class), store,
                "https://id.example/realms/shared", base, base + "/token",
                "collectra-machine", "test-secret", true);
    }

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void durableApplyPrecedesHttpAckAndRetryIsIdempotent() {
        consumer.synchronize("global-sub");
        consumer.synchronize("global-sub");
        var order = inOrder(store);
        order.verify(store, times(2)).apply("https://id.example/realms/shared",
                "global-sub", 2L, "BLOCKED");
        assertThat(acknowledgements.get()).isEqualTo(2);
    }

    @Test void failedLocalApplyMustNotSendAck() {
        doThrow(new IllegalStateException("database unavailable")).when(store)
                .apply("https://id.example/realms/shared", "global-sub", 2L, "BLOCKED");
        assertThatThrownBy(() -> consumer.synchronize("global-sub"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(acknowledgements.get()).isZero();
    }
}
