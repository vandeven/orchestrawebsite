package org.example.website.accounts.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/** The in-memory stores pass the same cases as the Firestore ones (ARC-6). */
class InMemoryStoresTest extends AccountStoresContract {

    private final InMemoryStorage storage = new InMemoryStorage();
    private Instant now = START;

    InMemoryStoresTest() {
        storage.config = new MasterAccountSetupTest.Config();
        storage.clock =
                new Clock() {
                    @Override
                    public java.time.ZoneId getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return now;
                    }
                };
    }

    @Override
    UserRepository users() {
        return storage;
    }

    @Override
    SessionRepository sessions() {
        return storage;
    }

    @Override
    RecoveryTokenRepository recoveryTokens() {
        return storage;
    }

    @Override
    AttemptStore attempts() {
        return storage;
    }

    @Override
    void moveClock(Duration by) {
        now = now.plus(by);
    }
}
