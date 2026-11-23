package org.example.website.platform.internal.limits;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.example.website.platform.api.limits.RateLimiter;

class InMemoryRateLimiterTest extends RateLimiterContract {

    private Instant now = START;
    private final InMemoryRateLimiter limiter =
            new InMemoryRateLimiter(
                    new Clock() {
                        @Override
                        public ZoneId getZone() {
                            return ZoneOffset.UTC;
                        }

                        @Override
                        public Clock withZone(ZoneId zone) {
                            return this;
                        }

                        @Override
                        public Instant instant() {
                            return now;
                        }
                    });

    @Override
    protected RateLimiter limiter() {
        return limiter;
    }

    @Override
    protected void moveClock(Duration by) {
        now = now.plus(by);
    }
}
