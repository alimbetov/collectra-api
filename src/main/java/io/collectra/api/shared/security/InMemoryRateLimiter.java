package io.collectra.api.shared.security;

import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void check(String key, int maximum, Duration duration) {
        Window value = windows.compute(key, (ignored, current) -> {
            Instant now = Instant.now();
            if (current == null || current.started.plus(duration).isBefore(now)) return new Window(now, 1);
            return new Window(current.started, current.count + 1);
        });
        if (value.count > maximum) throw new RateLimitExceededException();
    }
    private record Window(Instant started, int count) {}
}
