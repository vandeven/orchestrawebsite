package org.example.website.platform.internal.limits;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.website.platform.api.limits.RateLimiter;

/** The counters in memory, for tests and local work. Lost when the application stops. */
@ApplicationScoped
@Typed(InMemoryRateLimiter.class)
public class InMemoryRateLimiter implements RateLimiter {

    private record Window(int count, Instant start) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    @Inject
    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Hit hit(String key, Duration period) {
        Instant now = clock.instant();
        Window window =
                windows.compute(
                        key,
                        (k, old) ->
                                old == null || !now.isBefore(old.start().plus(period))
                                        ? new Window(1, now)
                                        : new Window(old.count() + 1, old.start()));
        return new Hit(window.count(), Duration.between(now, window.start().plus(period)));
    }

    /** For tests: forgets every counter. */
    public void clear() {
        windows.clear();
    }
}
