package io.collectra.api.shared.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OutboxRetryPolicy {
    private final int maxAttempts;

    public OutboxRetryPolicy(@Value("${collectra.messaging.outbox-max-attempts:6}") int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts;
    }

    public Duration delayForAttempt(int attempt) {
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
        return switch (attempt) {
            case 1 -> Duration.ZERO;
            case 2 -> Duration.ofMinutes(1);
            case 3 -> Duration.ofMinutes(5);
            case 4 -> Duration.ofMinutes(15);
            default -> Duration.ofHours(1);
        };
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
