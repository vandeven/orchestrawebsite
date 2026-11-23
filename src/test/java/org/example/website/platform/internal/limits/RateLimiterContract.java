package org.example.website.platform.internal.limits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.example.website.platform.api.limits.RateLimiter;
import org.junit.jupiter.api.Test;

/** The same cases for the in-memory and the Firestore counters (ARC-6). */
public abstract class RateLimiterContract {

    protected static final Instant START = Instant.parse("2026-11-23T10:00:00Z");

    protected abstract RateLimiter limiter();

    protected abstract void moveClock(Duration by);

    private static String key() {
        return "test-" + UUID.randomUUID();
    }

    @Test
    void everyRequestInThePeriodCounts() {
        String key = key();
        for (int i = 1; i <= 5; i++) {
            assertEquals(i, limiter().hit(key, Duration.ofHours(1)).count());
        }
    }

    @Test
    void keysCountSeparately() {
        assertEquals(1, limiter().hit(key(), Duration.ofHours(1)).count());
        assertEquals(1, limiter().hit(key(), Duration.ofHours(1)).count());
    }

    @Test
    void thePeriodStartsWithTheFirstRequestAndIsNotExtended() {
        String key = key();
        assertEquals(Duration.ofHours(1), limiter().hit(key, Duration.ofHours(1)).untilReset());
        moveClock(Duration.ofMinutes(40));
        var hit = limiter().hit(key, Duration.ofHours(1));
        assertEquals(2, hit.count());
        assertEquals(Duration.ofMinutes(20), hit.untilReset());
    }

    @Test
    void afterThePeriodTheCountStartsAgain() {
        String key = key();
        limiter().hit(key, Duration.ofHours(1));
        limiter().hit(key, Duration.ofHours(1));
        moveClock(Duration.ofHours(1));
        var hit = limiter().hit(key, Duration.ofHours(1));
        assertEquals(1, hit.count());
        assertTrue(hit.untilReset().compareTo(Duration.ofHours(1)) == 0);
    }

    @Test
    void requestsAtTheSameMomentAllCount() throws Exception {
        String key = key();
        int threads = 6;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            var done = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < threads; i++) {
                done.add(pool.submit(() -> limiter().hit(key, Duration.ofHours(1))));
            }
            for (var result : done) {
                result.get(60, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertEquals(threads + 1, limiter().hit(key, Duration.ofHours(1)).count());
        } finally {
            pool.shutdownNow();
        }
    }
}
