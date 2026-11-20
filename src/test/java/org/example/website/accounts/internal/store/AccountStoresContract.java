package org.example.website.accounts.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.website.accounts.api.users.Role;
import org.junit.jupiter.api.Test;

/**
 * The cases that every store must pass (ARC-6): the in-memory one and the Firestore one run the
 * same tests, so a fake that behaves differently from the real thing is found here. Every test uses
 * its own random ids and keys, so tests do not disturb each other in a shared database.
 */
public abstract class AccountStoresContract {

    /** The moment the stores see as "now", which a test can move. */
    public static final Instant START = Instant.parse("2026-11-18T10:00:00Z");

    public abstract UserRepository users();

    public abstract SessionRepository sessions();

    public abstract RecoveryTokenRepository recoveryTokens();

    public abstract AttemptStore attempts();

    public abstract void moveClock(Duration by);

    private static String random() {
        return UUID.randomUUID().toString();
    }

    private static UserRecord user(String email) {
        return new UserRecord(
                UUID.randomUUID(),
                email,
                Role.MEMBER,
                "hash-" + random(),
                Instant.parse("2026-11-01T08:30:00Z"),
                null,
                null);
    }

    // Users

    @Test
    void aStoredUserComesBackWithEveryField() {
        UserRecord saved =
                new UserRecord(
                        UUID.randomUUID(),
                        random() + "@example.test",
                        Role.ADMIN,
                        "argon2-hash",
                        Instant.parse("2026-11-01T08:30:00Z"),
                        Instant.parse("2026-11-17T20:15:30Z"),
                        Instant.parse("2026-11-02T09:00:00Z"));
        assertTrue(users().insertIfEmailFree(saved));
        assertEquals(saved, users().findById(saved.id()).orElseThrow());
        assertEquals(saved, users().findByEmail(saved.email()).orElseThrow());
        assertTrue(users().findAll().contains(saved));
    }

    @Test
    void optionalFieldsMayBeEmpty() {
        UserRecord saved =
                new UserRecord(
                        UUID.randomUUID(),
                        random() + "@example.test",
                        Role.MEMBER,
                        null,
                        Instant.parse("2026-11-01T08:30:00Z"),
                        null,
                        null);
        assertTrue(users().insertIfEmailFree(saved));
        assertEquals(saved, users().findById(saved.id()).orElseThrow());
    }

    @Test
    void anUnknownUserIsEmpty() {
        assertTrue(users().findById(UUID.randomUUID()).isEmpty());
        assertTrue(users().findByEmail(random() + "@example.test").isEmpty());
    }

    @Test
    void anAddressCanOnlyBeUsedOnce() {
        String email = random() + "@example.test";
        UserRecord first = user(email);
        UserRecord second = user(email);
        assertTrue(users().insertIfEmailFree(first));
        assertFalse(users().insertIfEmailFree(second));
        assertEquals(first.id(), users().findByEmail(email).orElseThrow().id());
        assertTrue(users().findById(second.id()).isEmpty(), "the refused user is not stored");
    }

    @Test
    void twoPeopleAddingTheSameAddressAtTheSameMomentCreateOne() throws Exception {
        String email = random() + "@example.test";
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                results.add(pool.submit(() -> users().insertIfEmailFree(user(email))));
            }
            int created = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    created++;
                }
            }
            assertEquals(1, created);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anUpdateReplacesTheUser() {
        UserRecord saved = user(random() + "@example.test");
        users().insertIfEmailFree(saved);
        UserRecord changed =
                saved.withRole(Role.ADMIN)
                        .withPasswordHash("new-hash")
                        .withLastLogin(Instant.parse("2026-11-18T09:00:00Z"));
        users().update(changed);
        assertEquals(changed, users().findById(saved.id()).orElseThrow());
        assertEquals(changed, users().findByEmail(saved.email()).orElseThrow());
    }

    @Test
    void updatingAUserThatDoesNotExistCreatesNothing() {
        UserRecord ghost = user(random() + "@example.test");
        users().update(ghost);
        assertTrue(users().findById(ghost.id()).isEmpty());
        assertTrue(users().findByEmail(ghost.email()).isEmpty());
    }

    @Test
    void deletingAUserFreesTheAddress() {
        String email = random() + "@example.test";
        UserRecord first = user(email);
        users().insertIfEmailFree(first);
        users().delete(first.id());
        assertTrue(users().findById(first.id()).isEmpty());
        assertTrue(users().findByEmail(email).isEmpty());
        UserRecord again = user(email);
        assertTrue(users().insertIfEmailFree(again), "the address is free again");
        users().delete(UUID.randomUUID()); // an unknown user is not an error
    }

    @Test
    void addressesWithSlashesAndOddCharactersWork() {
        UserRecord odd = user("a/b+c%d@example.test" + random());
        assertTrue(users().insertIfEmailFree(odd));
        assertEquals(odd.id(), users().findByEmail(odd.email()).orElseThrow().id());
    }

    // Sessions

    @Test
    void aSessionIsFoundByItsHashAndDeleted() {
        String hash = random();
        SessionRecord session =
                new SessionRecord(hash, UUID.randomUUID(), Instant.parse("2026-12-01T00:00:00Z"));
        sessions().save(session);
        assertEquals(session, sessions().findByTokenHash(hash).orElseThrow());
        sessions().delete(hash);
        assertTrue(sessions().findByTokenHash(hash).isEmpty());
        sessions().delete(hash); // again: not an error
        assertTrue(sessions().findByTokenHash(random()).isEmpty());
    }

    @Test
    void savingASessionAgainReplacesIt() {
        String hash = random();
        UUID user = UUID.randomUUID();
        sessions().save(new SessionRecord(hash, user, Instant.parse("2026-12-01T00:00:00Z")));
        sessions().save(new SessionRecord(hash, user, Instant.parse("2026-12-05T00:00:00Z")));
        assertEquals(
                Instant.parse("2026-12-05T00:00:00Z"),
                sessions().findByTokenHash(hash).orElseThrow().expiresAt());
    }

    @Test
    void deletingAllSessionsOfAUserLeavesTheOthers() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<String> mine = new ArrayList<>();
        for (int i = 0; i < 450; i++) { // more than one batch of deletes
            String hash = random();
            mine.add(hash);
            sessions().save(new SessionRecord(hash, user, Instant.parse("2026-12-01T00:00:00Z")));
        }
        String theirs = random();
        sessions().save(new SessionRecord(theirs, other, Instant.parse("2026-12-01T00:00:00Z")));

        sessions().deleteAllForUser(user);

        assertTrue(mine.stream().allMatch(h -> sessions().findByTokenHash(h).isEmpty()));
        assertTrue(sessions().findByTokenHash(theirs).isPresent());
    }

    // Recovery links

    @Test
    void aRecoveryLinkIsFoundByItsHashAndDeleted() {
        String hash = random();
        RecoveryTokenRecord token =
                new RecoveryTokenRecord(
                        hash, UUID.randomUUID(), Instant.parse("2026-11-18T11:00:00Z"));
        recoveryTokens().save(token);
        assertEquals(token, recoveryTokens().findByHash(hash).orElseThrow());
        recoveryTokens().deleteByHash(hash);
        assertTrue(recoveryTokens().findByHash(hash).isEmpty());
        recoveryTokens().deleteByHash(hash); // again: not an error
    }

    @Test
    void aNewLinkCanReplaceTheOldOnesOfAUser() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String a = random();
        String b = random();
        String c = random();
        recoveryTokens()
                .save(new RecoveryTokenRecord(a, user, Instant.parse("2026-11-18T11:00:00Z")));
        recoveryTokens()
                .save(new RecoveryTokenRecord(b, user, Instant.parse("2026-11-18T11:00:00Z")));
        recoveryTokens()
                .save(new RecoveryTokenRecord(c, other, Instant.parse("2026-11-18T11:00:00Z")));
        recoveryTokens().deleteForUser(user);
        assertTrue(recoveryTokens().findByHash(a).isEmpty());
        assertTrue(recoveryTokens().findByHash(b).isEmpty());
        assertTrue(recoveryTokens().findByHash(c).isPresent());
    }

    // Login counters

    @Test
    void aCounterThatDoesNotExistIsEmpty() {
        assertEquals(AttemptCounter.EMPTY, attempts().get(random()));
    }

    @Test
    void aCounterIsChangedAndKeptUntilItExpires() {
        String key = random();
        Instant until = START.plusSeconds(90);
        AttemptCounter changed =
                attempts()
                        .update(
                                key,
                                Duration.ofHours(1),
                                c -> new AttemptCounter(c.failures() + 1, START, until));
        assertEquals(new AttemptCounter(1, START, until), changed);
        assertEquals(changed, attempts().get(key));
        attempts()
                .update(
                        key,
                        Duration.ofHours(1),
                        c -> new AttemptCounter(c.failures() + 1, c.windowStart(), until));
        assertEquals(2, attempts().get(key).failures());

        moveClock(Duration.ofMinutes(59));
        assertEquals(2, attempts().get(key).failures(), "still inside the hour");
        moveClock(Duration.ofMinutes(2));
        assertEquals(AttemptCounter.EMPTY, attempts().get(key), "expired counters count as empty");
        assertEquals(
                1,
                attempts()
                        .update(
                                key,
                                Duration.ofHours(1),
                                c -> new AttemptCounter(c.failures() + 1, START, until))
                        .failures(),
                "an expired counter starts again at one");
    }

    @Test
    void aResetStartsAgain() {
        String key = random();
        attempts().update(key, Duration.ofHours(1), c -> new AttemptCounter(5, START, START));
        attempts().reset(key);
        assertEquals(AttemptCounter.EMPTY, attempts().get(key));
        attempts().reset(key); // again: not an error
    }

    @Test
    void failuresAtTheSameMomentAllCount() throws Exception {
        String key = random();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                done.add(
                        pool.submit(() ->
                                attempts()
                                        .update(
                                                key,
                                                Duration.ofHours(1),
                                                c ->
                                                        new AttemptCounter(
                                                                c.failures() + 1, START, START))));
            }
            for (Future<?> result : done) {
                result.get(60, TimeUnit.SECONDS);
            }
            assertEquals(threads, attempts().get(key).failures());
        } finally {
            pool.shutdownNow();
        }
    }
}
