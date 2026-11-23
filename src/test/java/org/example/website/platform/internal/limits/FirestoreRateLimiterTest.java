package org.example.website.platform.internal.limits;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.example.website.platform.api.database.Database;
import org.example.website.platform.api.limits.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The Firestore counters against the emulator, with the same cases as the in-memory ones. */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestoreRateLimiterTest extends RateLimiterContract {

    @Inject Database database;

    private final FirestoreRateLimiter limiter = new FirestoreRateLimiter();
    private Instant now;

    @BeforeEach
    void setUp() {
        now = START;
        limiter.database = database;
        limiter.clock =
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
                };
    }

    @Override
    protected RateLimiter limiter() {
        return limiter;
    }

    @Override
    protected void moveClock(Duration by) {
        now = now.plus(by);
    }
}
