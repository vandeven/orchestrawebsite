package org.example.website.accounts.internal.store.firestore;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.example.website.accounts.internal.store.AccountStoresContract;
import org.example.website.accounts.internal.store.AttemptStore;
import org.example.website.accounts.internal.store.RecoveryTokenRepository;
import org.example.website.accounts.internal.store.SessionRepository;
import org.example.website.accounts.internal.store.UserRepository;
import org.example.website.platform.api.database.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The Firestore stores against the Firestore emulator (ARC-6). They need it: start it with {@code
 * scripts/firestore-emulator.sh} and set FIRESTORE_EMULATOR_HOST (the script says how). The
 * pipeline does the same. Without the variable these tests are skipped.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestoreStoresTest extends AccountStoresContract {

    @Inject Database database;
    @Inject FirestoreUsers users;
    @Inject FirestoreSessions sessions;
    @Inject FirestoreRecoveryTokens recoveryTokens;

    private final FirestoreAttempts attempts = new FirestoreAttempts();
    private Instant now;

    @BeforeEach
    void setUp() {
        now = START;
        attempts.database = database;
        attempts.clock =
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
    public UserRepository users() {
        return users;
    }

    @Override
    public SessionRepository sessions() {
        return sessions;
    }

    @Override
    public RecoveryTokenRepository recoveryTokens() {
        return recoveryTokens;
    }

    @Override
    public AttemptStore attempts() {
        return attempts;
    }

    @Override
    public void moveClock(Duration by) {
        now = now.plus(by);
    }
}
