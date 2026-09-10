package io.collectra.api.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class OutboxRetryPolicyUnitTest {
    @Test
    void usesBoundedRetrySchedule() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(6);

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ZERO);
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.delayForAttempt(5)).isEqualTo(Duration.ofHours(1));
        assertThat(policy.maxAttempts()).isEqualTo(6);
    }
}
