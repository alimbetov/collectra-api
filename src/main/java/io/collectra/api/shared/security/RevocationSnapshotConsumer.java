package io.collectra.api.shared.security;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reconciles every VERIFIED linked subject, even if an event was lost. OAuth client credentials are
 * never reused as end-user credentials.
 */
@Component
public class RevocationSnapshotConsumer {
    private static final Logger log = LoggerFactory.getLogger(RevocationSnapshotConsumer.class);
    private static final int BACKGROUND_BATCH_SIZE = 50;

    private final JdbcClient db;
    private final RevocationSnapshotStore store;
    private String scanAfterSubject = "";
    private final RestClient http;
    private final String issuer, base, tokenUrl, clientId, secret;
    private final boolean enabled;

    public RevocationSnapshotConsumer(
            JdbcClient db,
            RevocationSnapshotStore store,
            @Value("${collectra.security.keycloak.issuer:}") String issuer,
            @Value("${collectra.security.keycloak.revocation-base-url:}") String base,
            @Value("${collectra.security.keycloak.revocation-token-url:}") String tokenUrl,
            @Value("${collectra.security.keycloak.revocation-client-id:}") String clientId,
            @Value("${collectra.security.keycloak.revocation-client-secret:}") String secret,
            @Value("${collectra.security.keycloak.revocation-consumer-enabled:false}")
                    boolean enabled) {
        this.db = db;
        this.store = store;
        this.issuer = issuer;
        this.base = base;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.secret = secret;
        this.enabled = enabled;
        this.http =
                RestClient.builder()
                        .requestFactory(
                                new org.springframework.http.client
                                        .SimpleClientHttpRequestFactory() {
                                    {
                                        setConnectTimeout(2000);
                                        setReadTimeout(3000);
                                    }
                                })
                        .build();
    }

    @Scheduled(fixedDelayString = "${collectra.security.keycloak.revocation-poll-ms:5000}")
    public synchronized void reconcile() {
        if (!enabled) return;
        if (issuer.isBlank()
                || base.isBlank()
                || tokenUrl.isBlank()
                || clientId.isBlank()
                || secret.isBlank())
            throw new IllegalStateException("Revocation consumer credentials missing");
        // One bounded page per tick. A full unbounded scan can monopolize the scheduler,
        // while on-demand refresh handles stale identities that are actually signing in.
        // The keyset cursor advances even if an individual remote subject lookup fails.
        var subjects =
                db.sql(
                                "SELECT subject_id FROM collectra_identity_links "
                                        + "WHERE issuer=:issuer AND status='VERIFIED' "
                                        + "AND subject_id > :after ORDER BY subject_id LIMIT :limit")
                        .param("issuer", issuer)
                        .param("after", scanAfterSubject)
                        .param("limit", BACKGROUND_BATCH_SIZE)
                        .query(String.class)
                        .list();
        if (subjects.isEmpty()) {
            scanAfterSubject = "";
            return;
        }
        String token = serviceToken();
        int failures = 0;
        for (String subject : subjects) {
            try {
                synchronize(subject, token);
            } catch (RuntimeException unavailable) {
                // Fail closed. Never log subjects, bearer tokens or IdP response bodies.
                failures++;
            }
        }
        scanAfterSubject = subjects.get(subjects.size() - 1);
        if (failures > 0) {
            log.warn(
                    "Revocation background reconciliation failed for {} of {} subjects; stale access remains fail-closed",
                    failures,
                    subjects.size());
        }
    }

    @SuppressWarnings("unchecked")
    private String serviceToken() {
        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        Map<String, Object> response =
                http.post()
                        .uri(tokenUrl)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(Map.class);
        if (response == null || !(response.get("access_token") instanceof String token))
            throw new IllegalStateException("No service token");
        return token;
    }

    public void synchronize(String subject) {
        if (!enabled) throw new IllegalStateException("Consumer disabled");
        synchronize(subject, serviceToken());
    }

    private void synchronize(String subject, String token) {
        var snapshot =
                http.get()
                        .uri(
                                base + "/api/v1/internal/revocations/{subject}?issuer={issuer}",
                                subject,
                                issuer)
                        .headers(h -> h.setBearerAuth(token))
                        .retrieve()
                        .body(Snapshot.class);
        if (snapshot == null || snapshot.version() < 1)
            throw new IllegalStateException("Uninitialized remote access ledger");
        // Transaction commits before ACK request; duplicates are safe.
        store.apply(issuer, subject, snapshot.version(), snapshot.state());
        http.post()
                .uri(base + "/api/v1/internal/revocations/ack")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Ack(issuer, subject, snapshot.version()))
                .retrieve()
                .toBodilessEntity();
    }

    public record Snapshot(String state, long version) {}

    public record Ack(String issuer, String subject, long version) {}
}
