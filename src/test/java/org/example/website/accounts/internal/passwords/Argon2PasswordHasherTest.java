package org.example.website.accounts.internal.passwords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.junit.jupiter.api.Test;

/** LOG-16 to LOG-20 and LOG-23: how passwords are hashed. */
class Argon2PasswordHasherTest {

    private static final String PEPPER = "FAKE-NOT-A-SECRET-pepper-0123456789abcdef-ABCDEF";

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher(PEPPER, 4, 3_000);

    @Test
    void theStoredValueIsAnArgon2idHashWithTheExpectedSettings() {
        String hash = hasher.hash("correct horse battery staple");
        String[] parts = hash.split("\\$");
        assertEquals("argon2id", parts[1]);
        assertEquals("v=19", parts[2]);
        assertEquals("m=19456,t=2,p=1", parts[3]); // LOG-16: the OWASP minimum, written in the hash
        assertEquals(16, Base64.getDecoder().decode(parts[4]).length, "a 16 byte salt (LOG-17)");
        assertEquals(32, Base64.getDecoder().decode(parts[5]).length);
    }

    @Test
    void thePlainPasswordIsNeverInTheStoredValue() {
        String password = "correct horse battery staple";
        String hash = hasher.hash(password);
        assertFalse(hash.contains(password));
        assertFalse(hash.contains("correct"));
        assertFalse(hash.contains(PEPPER));
    }

    @Test
    void theSamePasswordGivesADifferentHashEveryTime() {
        // A unique salt per password (LOG-17)
        assertNotEquals(hasher.hash("same password 123"), hasher.hash("same password 123"));
    }

    @Test
    void theRightPasswordVerifiesAndAWrongOneDoesNot() {
        String hash = hasher.hash("correct horse battery staple");
        assertTrue(hasher.verify("correct horse battery staple", hash));
        assertFalse(hasher.verify("correct horse battery stapl", hash));
        assertFalse(hasher.verify("Correct horse battery staple", hash));
        assertFalse(hasher.verify("", hash));
    }

    @Test
    void aDifferentPepperDoesNotVerify() {
        // LOG-18: a stolen database without the pepper is not enough
        String hash = hasher.hash("correct horse battery staple");
        Argon2PasswordHasher other =
                new Argon2PasswordHasher("another-pepper-that-is-also-at-least-32-chars", 4, 3_000);
        assertFalse(other.verify("correct horse battery staple", hash));
        assertTrue(hasher.verify("correct horse battery staple", hash));
    }

    @Test
    void unicodeAndLongPasswordsWork() {
        String password = "pässwörd-日本語-🎼".repeat(5);
        assertTrue(hasher.verify(password, hasher.hash(password)));
        String longest = "x".repeat(128);
        assertTrue(hasher.verify(longest, hasher.hash(longest)));
    }

    @Test
    void brokenOrUnknownHashStringsNeverVerify() {
        for (String broken :
                new String[] {
                    null,
                    "",
                    "plain-text-password",
                    "$argon2id$v=19$m=19456,t=2,p=1$",
                    "$argon2id$v=19$m=19456,t=2,p=1$!!!$!!!",
                    "$argon2i$v=19$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0c2FsdA$aGFzaA",
                    "$argon2id$v=16$m=19456,t=2,p=1$c2FsdHNhbHRzYWx0c2FsdA$aGFzaA",
                    "$argon2id$v=19$m=0,t=0,p=0$c2FsdHNhbHRzYWx0c2FsdA$aGFzaA",
                    "$argon2id$v=19$x=1$c2FsdHNhbHRzYWx0c2FsdA$aGFzaA"
                }) {
            assertFalse(hasher.verify("anything", broken), "must not verify: " + broken);
        }
    }

    @Test
    void aHashWithWeakerSettingsIsReplacedAfterALogin() {
        // LOG-20: a hash that was made with older settings needs a new hash
        String current = hasher.hash("password for the test");
        assertFalse(hasher.needsRehash(current));

        String[] parts = current.split("\\$");
        String weaker = "$argon2id$v=19$m=4096,t=1,p=1$" + parts[4] + "$" + parts[5];
        assertTrue(hasher.needsRehash(weaker));
        assertTrue(hasher.needsRehash("garbage"));
        assertTrue(hasher.needsRehash(null));

        String stronger = "$argon2id$v=19$m=65536,t=3,p=1$" + parts[4] + "$" + parts[5];
        assertFalse(hasher.needsRehash(stronger), "stronger settings are left alone");
    }

    @Test
    void aHashMadeWithOlderSettingsStillVerifiesAndIsFlaggedForAnUpgrade() {
        // The settings are read from the hash itself, so settings can be raised later (LOG-16)
        String old = hasher.hashWith("password for the test", 4_096, 1, 1);
        assertTrue(hasher.verify("password for the test", old));
        assertFalse(hasher.verify("another password!", old));
        assertTrue(hasher.needsRehash(old));
    }

    @Test
    void onlyAFewHashesRunAtTheSameTimeAndTheRestGetABusyMessage() throws Exception {
        // LOG-23: one slot, a short wait; the second request must get "busy", not a wrong password
        Argon2PasswordHasher single = new Argon2PasswordHasher(PEPPER, 1, 10);
        String hash = single.hash("password for the test");

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        try {
            java.util.List<Future<String>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(
                        pool.submit(() -> {
                            go.await();
                            try {
                                return single.verify("password for the test", hash)
                                        ? "ok"
                                        : "wrong";
                            } catch (ServiceUnavailable busy) {
                                return "busy";
                            }
                        }));
            }
            go.countDown();
            int ok = 0;
            int busy = 0;
            for (Future<String> result : results) {
                String value = result.get(30, TimeUnit.SECONDS);
                assertNotEquals("wrong", value, "a correct password is never reported as wrong");
                if (value.equals("ok")) {
                    ok++;
                } else {
                    busy++;
                }
            }
            assertTrue(ok >= 1, "at least one request is served");
            assertTrue(busy >= 1, "the extra requests get the busy message");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theHashTakesAMeasurableTime() {
        // A guard against settings that were made too weak by mistake (LOG-16): well above a
        // plain SHA-256, well below a second on a build machine
        hasher.hash("warm up");
        long start = System.nanoTime();
        hasher.hash("measure me");
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis >= 3, "a hash took only " + millis + " ms, the settings look too weak");
        assertTrue(millis < 2_000, "a hash took " + millis + " ms, the settings look too slow");
    }
}
