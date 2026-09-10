package io.collectra.api.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyUnitTest {
    @Test
    void usesBoundedRetrySchedule() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(6);

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ZERO);
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.delayForAttempt(5)).isEqualTo(Duration.ofHours(1));
        assertThat(policy.delayForAttempt(10)).isEqualTo(Duration.ofHours(1));
        assertThat(policy.maxAttempts()).isEqualTo(6);
    }

    @Test
    void rejectsInvalidMaxAttempts() {
        assertThatThrownBy(() -> new OutboxRetryPolicy(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
        assertThatThrownBy(() -> new OutboxRetryPolicy(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    @Test
    void rejectsInvalidAttemptNumber() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(6);

        assertThatThrownBy(() -> policy.delayForAttempt(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
        assertThatThrownBy(() -> policy.delayForAttempt(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
    }
}
