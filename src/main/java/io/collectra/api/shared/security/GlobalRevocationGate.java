package io.collectra.api.shared.security;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Local, fail-closed authorization with bounded read-through refresh for stale/missing snapshots.
 * Fresh BLOCKED snapshots are never bypassed. An unavailable AC is never treated as ACTIVE.
 * Background reconciliation remains responsible for blocked/unblock propagation.
 */
@Component
public class GlobalRevocationGate {
    private static final int MAX_REFRESH_WAIT_SECONDS = 10;

    private final JdbcClient db;
    private final RevocationSnapshotConsumer consumer;
    private final Duration maxAge;
    private final Semaphore refreshSlots;
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> inFlight =
            new ConcurrentHashMap<>();

    @Autowired
    public GlobalRevocationGate(
            JdbcClient db,
            RevocationSnapshotConsumer consumer,
            @Value("${collectra.security.keycloak.revocation-max-age:PT30S}") Duration maxAge,
            @Value("${collectra.security.keycloak.revocation-on-demand-max-concurrent:8}")
                    int maxConcurrent) {
        this.db = db;
        this.consumer = consumer;
        this.maxAge = maxAge;
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("Revocation refresh concurrency must be positive");
        }
        this.refreshSlots = new Semaphore(maxConcurrent);
    }

    /** Local-only constructor for fail-closed storage tests. */
    GlobalRevocationGate(JdbcClient db, Duration maxAge) {
        this(db, null, maxAge, 1);
    }

    public boolean allows(String issuer, String subject) {
        if (issuer == null
                || issuer.isBlank()
                || subject == null
                || subject.isBlank()
                || maxAge.isNegative()
                || maxAge.isZero()) {
            return false;
        }
        try {
            LocalState local = localState(issuer, subject);
            if (local.fresh()) {
                return local.active();
            }
        } catch (DataAccessException unavailable) {
            return false;
        }
        // A cold identity should not wait for a full scan of every enrolled user.
        // Only the authoritative AC can refresh a stale or missing state.
        return refreshOnDemand(issuer, subject);
    }

    private LocalState localState(String issuer, String subject) {
        return db.sql(
                        """
                        SELECT state,
                               refreshed_at >= now() - (:max_age * interval '1 second') AS fresh
                        FROM collectra_global_access_snapshots
                        WHERE issuer=:iss AND subject_id=:sub
                        """)
                .param("iss", issuer)
                .param("sub", subject)
                .param("max_age", maxAge.toMillis() / 1000.0)
                .query(
                        (rs, n) ->
                                new LocalState(
                                        rs.getBoolean("fresh"),
                                        "ACTIVE".equals(rs.getString("state"))))
                .optional()
                .orElse(new LocalState(false, false));
    }

    private boolean refreshOnDemand(String issuer, String subject) {
        if (consumer == null) {
            return false;
        }
        String key = issuer + "\u0000" + subject;
        var existing = inFlight.get(key);
        if (existing != null) {
            return await(existing);
        }
        // Overload must never expand into an unbounded queue of IdP calls.
        if (!refreshSlots.tryAcquire()) {
            return false;
        }
        var flight = new CompletableFuture<Boolean>();
        existing = inFlight.putIfAbsent(key, flight);
        if (existing != null) {
            refreshSlots.release();
            return await(existing);
        }
        try {
            try {
                consumer.synchronize(subject);
                LocalState updated = localState(issuer, subject);
                boolean allowed = updated.fresh() && updated.active();
                flight.complete(allowed);
                return allowed;
            } catch (RuntimeException unavailable) {
                flight.complete(false);
                return false;
            }
        } finally {
            inFlight.remove(key, flight);
            refreshSlots.release();
        }
    }

    private boolean await(CompletableFuture<Boolean> flight) {
        try {
            return flight.get(MAX_REFRESH_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException unavailable) {
            return false;
        }
    }

    private record LocalState(boolean fresh, boolean active) {}
}
