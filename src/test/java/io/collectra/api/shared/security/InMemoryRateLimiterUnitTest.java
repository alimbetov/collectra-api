package io.collectra.api.shared.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterUnitTest {
    @Test
    void rejectsRequestsAboveConfiguredWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        String key = UUID.randomUUID().toString();
        assertThatCode(() -> limiter.check(key, 2, Duration.ofMinutes(1))).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(key, 2, Duration.ofMinutes(1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(key, 2, Duration.ofMinutes(1)))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void limitsKeysIndependently() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        limiter.check("tenant-a", 1, Duration.ofMinutes(1));
        assertThatCode(() -> limiter.check("tenant-b", 1, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }
}
