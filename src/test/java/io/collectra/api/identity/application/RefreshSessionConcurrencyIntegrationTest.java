package io.collectra.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.shared.error.InvalidRefreshTokenException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RefreshSessionConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired AuthService authService;

    @Test
    void sameRefreshTokenCanBeConsumedOnlyOnceConcurrently() throws Exception {
        AuthService.AuthTokens initial =
                authService.register(
                        "refresh-race-" + UUID.randomUUID(),
                        "Refresh Race",
                        "refresh-race-" + UUID.randomUUID() + "@example.test",
                        "StrongPassword123!");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<RefreshAttempt> refresh =
                () -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent refresh did not start in time");
                    }
                    try {
                        return RefreshAttempt.succeeded(
                                authService.refresh(initial.refreshToken()).refreshToken());
                    } catch (InvalidRefreshTokenException expected) {
                        return RefreshAttempt.rejected();
                    }
                };

        try {
            Future<RefreshAttempt> first = executor.submit(refresh);
            Future<RefreshAttempt> second = executor.submit(refresh);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RefreshAttempt> results =
                    List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(results).filteredOn(RefreshAttempt::success).hasSize(1);
            assertThat(results).filteredOn(result -> !result.success()).hasSize(1);

            String rotatedToken =
                    results.stream()
                            .filter(RefreshAttempt::success)
                            .map(RefreshAttempt::refreshToken)
                            .findFirst()
                            .orElseThrow();

            assertThatThrownBy(() -> authService.refresh(rotatedToken))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    private record RefreshAttempt(boolean success, String refreshToken) {
        static RefreshAttempt succeeded(String refreshToken) {
            return new RefreshAttempt(true, refreshToken);
        }

        static RefreshAttempt rejected() {
            return new RefreshAttempt(false, null);
        }
    }
}
