package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MessageRetryPolicyTest {
    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");
    private final MessageRetryPolicy policy = new MessageRetryPolicy();

    @Test
    void usesDeterministicRetrySchedule() {
        assertThat(policy.nextRetryAt(1, NOW)).isEqualTo(NOW.plusSeconds(60));
        assertThat(policy.nextRetryAt(2, NOW)).isEqualTo(NOW.plusSeconds(600));
        assertThat(policy.nextRetryAt(3, NOW)).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void fourthAttemptIsExhaustedAndHasNoRetryDelay() {
        assertThat(policy.exhausted(3)).isFalse();
        assertThat(policy.exhausted(4)).isTrue();
        assertThat(policy.exhausted(5)).isTrue();
        assertThatThrownBy(() -> policy.nextRetryAt(4, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
