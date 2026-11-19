package org.example.website.accounts.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Counts requests per key in a fixed period, with the same shared counters as the login limits
 * (LOG-13, USR-22). Used for recovery requests and for wrong recovery links.
 */
@ApplicationScoped
class RequestCounter {

    @Inject AttemptStore store;
    @Inject Clock clock;

    /** Counts one more request and returns how many there have been in the current period. */
    Hit hit(String key, Duration period) {
        Instant now = clock.instant();
        AttemptCounter counter =
                store.update(
                        key,
                        period,
                        old -> {
                            boolean fresh =
                                    old.failures() == 0
                                            || !now.isBefore(old.windowStart().plus(period));
                            return fresh
                                    ? new AttemptCounter(1, now, Instant.EPOCH)
                                    : new AttemptCounter(
                                            old.failures() + 1,
                                            old.windowStart(),
                                            old.blockedUntil());
                        });
        return new Hit(
                counter.failures(), Duration.between(now, counter.windowStart().plus(period)));
    }

    /** The count so far and how long until the period ends. */
    record Hit(int count, Duration untilReset) {}
}
