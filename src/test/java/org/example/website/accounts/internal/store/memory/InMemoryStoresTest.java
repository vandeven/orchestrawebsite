package org.example.website.accounts.internal.store.memory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.example.website.accounts.internal.TestAccountsConfig;
import org.example.website.accounts.internal.store.AccountStoresContract;
import org.example.website.accounts.internal.store.AttemptStore;
import org.example.website.accounts.internal.store.RecoveryTokenRepository;
import org.example.website.accounts.internal.store.SessionRepository;
import org.example.website.accounts.internal.store.UserRepository;

/** The in-memory stores pass the same cases as the Firestore ones (ARC-6). */
class InMemoryStoresTest extends AccountStoresContract {

    private final InMemoryStorage storage;
    private Instant now = START;

    InMemoryStoresTest() {
        Clock clock =
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
        storage = new InMemoryStorage(new TestAccountsConfig(), clock);
    }

    @Override
    public UserRepository users() {
        return storage;
    }

    @Override
    public SessionRepository sessions() {
        return storage;
    }

    @Override
    public RecoveryTokenRepository recoveryTokens() {
        return storage;
    }

    @Override
    public AttemptStore attempts() {
        return storage;
    }

    @Override
    public void moveClock(Duration by) {
        now = now.plus(by);
    }
}
