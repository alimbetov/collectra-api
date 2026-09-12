package io.collectra.api.communication.infrastructure.kumomta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

class KumoMtaClientHttpTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestMethod = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    private HttpServer server;
    private ExecutorService executor;
    private volatile int status;
    private volatile String response;
    private volatile long responseDelayMillis;

    @BeforeEach
    void startServer() throws IOException {
        status = 200;
        response = "{\"success_count\":1,\"fail_count\":0,\"failed_recipients\":[],\"errors\":[]}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext(KumoMtaClient.INJECT_PATH, this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void postsExactJsonContractAndBasicAuthToInjectionEndpoint() throws Exception {
        KumoMtaProperties properties = properties(Duration.ofSeconds(1));
        properties.setUsername("collectra");
        properties.setPassword("secret");

        KumoMtaInjectResponse result = new KumoMtaClient(properties).inject(request());

        assertThat(result.acceptedSingleRecipient()).isTrue();
        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(requestPath.get()).isEqualTo("/api/inject/v1");
        assertThat(contentType.get()).startsWith("application/json");
        assertThat(authorization.get()).isEqualTo("Basic Y29sbGVjdHJhOnNlY3JldA==");
        JsonNode body = json.readTree(requestBody.get());
        assertThat(body.path("envelope_sender").asText()).isEqualTo("bounce@collectra.kz");
        assertThat(body.path("content").path("headers").path("Subject").asText())
                .isEqualTo("Subject");
        assertThat(body.path("content").path("html_body").asText()).isEqualTo("<p>Body</p>");
        assertThat(body.path("recipients").get(0).path("email").asText())
                .isEqualTo("client@example.com");
        assertThat(body.path("recipients").get(0).path("metadata").path("message_id").asText())
                .isEqualTo("message-1");
        assertThat(body.path("template_dialect").asText()).isEqualTo("Static");
        assertThat(body.path("deferred_generation").asBoolean()).isFalse();
        assertThat(body.path("deferred_spool").asBoolean()).isFalse();
    }

    @Test
    void exposesRetryableAndPermanentHttpStatusesForClassification() {
        for (int expectedStatus : List.of(422, 429, 503)) {
            status = expectedStatus;
            response = "{\"error\":\"provider failure\"}";

            assertThatThrownBy(
                            () ->
                                    new KumoMtaClient(properties(Duration.ofSeconds(1)))
                                            .inject(request()))
                    .isInstanceOf(RestClientResponseException.class)
                    .extracting(
                            failure ->
                                    ((RestClientResponseException) failure)
                                            .getStatusCode()
                                            .value())
                    .isEqualTo(expectedStatus);
        }
    }

    @Test
    void readTimeoutIsBounded() {
        responseDelayMillis = 250;

        assertThatThrownBy(
                        () ->
                                new KumoMtaClient(properties(Duration.ofMillis(50)))
                                        .inject(request()))
                .isInstanceOf(ResourceAccessException.class);
    }

    private KumoMtaProperties properties(Duration readTimeout) {
        KumoMtaProperties properties = new KumoMtaProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setEnvelopeSender("bounce@collectra.kz");
        properties.setReadTimeout(readTimeout);
        return properties;
    }

    private KumoMtaInjectRequest request() {
        return new KumoMtaInjectRequest(
                "bounce@collectra.kz",
                new KumoMtaInjectRequest.Content(
                        Map.of("From", "Collectra <noreply@collectra.kz>", "Subject", "Subject"),
                        "<p>Body</p>"),
                List.of(
                        new KumoMtaRecipient(
                                "client@example.com", Map.of("message_id", "message-1"))),
                "Static",
                false,
                false);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestMethod.set(exchange.getRequestMethod());
        requestPath.set(exchange.getRequestURI().getPath());
        contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (responseDelayMillis > 0) {
            try {
                Thread.sleep(responseDelayMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
