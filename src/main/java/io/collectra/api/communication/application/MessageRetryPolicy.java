package io.collectra.api.communication.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class MessageRetryPolicy {
    public static final int MAX_ATTEMPTS = 4;

    public boolean exhausted(int attemptCount) {
        return attemptCount >= MAX_ATTEMPTS;
    }

    public Instant nextRetryAt(int attemptCount, Instant now) {
        Objects.requireNonNull(now, "now is required");
        Duration delay =
                switch (attemptCount) {
                    case 1 -> Duration.ofMinutes(1);
                    case 2 -> Duration.ofMinutes(10);
                    case 3 -> Duration.ofHours(1);
                    default ->
                            throw new IllegalArgumentException(
                                    "No retry delay for attempt " + attemptCount);
                };
        return now.plus(delay);
    }
}
