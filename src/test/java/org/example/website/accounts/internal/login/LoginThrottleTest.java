package org.example.website.accounts.internal.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.LogRecord;
import org.example.website.TestAccounts;
import org.example.website.TestLogs;
import org.example.website.accounts.api.login.LoginResult;
import org.example.website.accounts.api.login.LoginService;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.accounts.internal.config.AccountsConfig;
import org.example.website.accounts.internal.security.Fingerprints;
import org.example.website.accounts.internal.store.memory.InMemoryStorage;
import org.example.website.platform.TestClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** LOG-13: the three limits on failed logins, with a clock that tests control. */
@QuarkusTest
class LoginThrottleTest {

    @Inject TestAccounts accounts;
    @Inject LoginService login;
    @Inject LoginThrottle throttle;
    @Inject InMemoryStorage storage;
    @Inject TestClock clock;
    @Inject Fingerprints fingerprints;
    @Inject AccountsConfig config;

    @AfterEach
    void resetTheClock() {
        clock.reset();
    }

    private static String visitor() {
        return TestAccounts.randomAddress();
    }

    private LoginResult wrong(UserSummary user, String address) {
        return login.login(user.email(), "wrong-password-xyz", address);
    }

    private LoginResult right(UserSummary user, String address) {
        return login.login(user.email(), TestAccounts.PASSWORD, address);
    }

    /** The wait as the page reports it: whole seconds, rounded up. */
    private static long seconds(LoginResult result) {
        return (((LoginResult.Throttled) result).retryAfter().toMillis() + 999) / 1000;
    }

    // One account from one visitor: a growing wait, not a lock

    @Test
    void theWaitDoublesWithEveryFailureUpToFiveMinutes() {
        assertEquals(Duration.ofSeconds(1), throttle.delayFor(1));
        assertEquals(Duration.ofSeconds(2), throttle.delayFor(2));
        assertEquals(Duration.ofSeconds(4), throttle.delayFor(3));
        assertEquals(Duration.ofSeconds(8), throttle.delayFor(4));
        assertEquals(Duration.ofSeconds(256), throttle.delayFor(9));
        assertEquals(Duration.ofSeconds(300), throttle.delayFor(10), "capped at 5 minutes");
        assertEquals(Duration.ofSeconds(300), throttle.delayFor(500));
    }

    @Test
    void anAttemptDuringTheWaitIsRefusedEvenWithTheRightPassword() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String address = visitor();

        assertTrue(wrong(user, address) instanceof LoginResult.Failed);
        LoginResult during = right(user, address);
        assertTrue(
                during instanceof LoginResult.Throttled,
                "the right password is refused during the wait");
        assertTrue(seconds(during) <= 1);
    }

    @Test
    void afterTheWaitTheNextAttemptIsAllowedAndTheNextWaitIsLonger() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String address = visitor();

        wrong(user, address); // wait 1 second
        clock.advance(Duration.ofSeconds(1));
        assertTrue(
                wrong(user, address) instanceof LoginResult.Failed, "allowed again after 1 second");

        LoginResult second = right(user, address); // wait 2 seconds now
        assertTrue(second instanceof LoginResult.Throttled);
        assertEquals(2, seconds(second));
        clock.advance(Duration.ofSeconds(1));
        assertTrue(
                right(user, address) instanceof LoginResult.Throttled,
                "1 of 2 seconds is not enough");
        clock.advance(Duration.ofSeconds(1));
        assertTrue(
                right(user, address) instanceof LoginResult.LoggedIn, "it is a wait, not a lock");
    }

    @Test
    void aRightPasswordEndsTheWaitingForThatAccountAndVisitor() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String address = visitor();
        for (int i = 0; i < 3; i++) {
            wrong(user, address);
            clock.advance(Duration.ofSeconds(10));
        }
        assertTrue(right(user, address) instanceof LoginResult.LoggedIn);
        // The counter is gone: the next failure starts at one second again
        wrong(user, address);
        assertEquals(1, seconds(right(user, address)));
    }

    @Test
    void anotherVisitorIsNotSlowedDownByTheFailuresOfThisOne() {
        // Nobody can lock a member out on purpose (LOG-13)
        UserSummary member = accounts.newUser(Role.MEMBER);
        String attacker = visitor();
        for (int i = 0; i < 12; i++) {
            wrong(member, attacker);
            clock.advance(Duration.ofMinutes(10));
        }
        wrong(member, attacker); // the attacker is now waiting a long time
        assertTrue(right(member, attacker) instanceof LoginResult.Throttled);

        assertTrue(
                right(member, visitor()) instanceof LoginResult.LoggedIn,
                "the member's own visitor is fine");
    }

    @Test
    void failuresForOneAccountDoNotSlowDownAnotherAccountFromTheSameVisitor() {
        UserSummary a = accounts.newUser(Role.MEMBER);
        UserSummary b = accounts.newUser(Role.MEMBER);
        String address = visitor();
        wrong(a, address);
        assertTrue(right(b, address) instanceof LoginResult.LoggedIn);
    }

    @Test
    void anAddressWithoutAnAccountIsSlowedDownInTheSameWay() {
        // Otherwise the waiting would show which addresses have an account
        String address = visitor();
        String unknown = "nobody-" + UUID.randomUUID() + "@example.test";
        assertTrue(
                login.login(unknown, "wrong-password-xyz", address) instanceof LoginResult.Failed);
        LoginResult second = login.login(unknown, "wrong-password-xyz", address);
        assertTrue(second instanceof LoginResult.Throttled);
        assertEquals(1, seconds(second));
    }

    @Test
    void theCountersExpireAfterTheCountingPeriod() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String address = visitor();
        for (int i = 0; i < 4; i++) {
            wrong(user, address);
            clock.advance(Duration.ofMinutes(1));
        }
        clock.advance(Duration.ofHours(2));
        wrong(user, address); // starts again at one
        assertEquals(1, seconds(right(user, address)));
    }

    // One account from everybody

    @Test
    void anAccountThatIsAttackedFromManyPlacesIsSlowedDownForEveryoneAndTheOwnerIsAlerted() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        List<LogRecord> logs =
                TestLogs.capture(() -> {
                    for (int i = 0; i < 100; i++) {
                        assertTrue(
                                wrong(user, visitor()) instanceof LoginResult.Failed,
                                "attempt " + i);
                    }
                });

        // 100 failures from 100 different visitors: now everyone waits, also with the right
        // password
        LoginResult next = right(user, visitor());
        assertTrue(next instanceof LoginResult.Throttled);
        assertEquals(30, seconds(next), "one attempt every 30 seconds");

        long alerts = logs.stream().filter(r -> TestLogs.everything(r).contains("ALERT")).count();
        assertEquals(1, alerts, "the owner is alerted once, when the limit is reached");
        for (LogRecord record : logs) {
            assertFalse(
                    TestLogs.everything(record).contains(user.email()), "no address in the log");
        }

        // It is a slow down, not a lock: the member can still log in after the wait
        clock.advance(Duration.ofSeconds(31));
        assertTrue(right(user, visitor()) instanceof LoginResult.LoggedIn);
    }

    @Test
    void theAccountLimitEndsWithTheCountingPeriod() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        for (int i = 0; i < 100; i++) {
            wrong(user, visitor());
        }
        assertTrue(right(user, visitor()) instanceof LoginResult.Throttled);
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertTrue(right(user, visitor()) instanceof LoginResult.LoggedIn);
    }

    // One visitor across all accounts: password spraying

    @Test
    void aVisitorWhoTriesManyAccountsIsRefusedForTheRestOfTheHour() {
        String sprayer = visitor();
        UserSummary victim = accounts.newUser(Role.MEMBER);
        List<LogRecord> logs =
                TestLogs.capture(() -> {
                    for (int i = 0; i < 200; i++) {
                        login.login("target-" + i + "@example.test", "Spring2026!pw", sprayer);
                    }
                });
        assertTrue(
                logs.stream()
                        .anyMatch(r -> TestLogs.everything(r).contains("is refused for the rest")));

        LoginResult refused = right(victim, sprayer);
        assertTrue(
                refused instanceof LoginResult.Throttled,
                "even a right password from that visitor");
        assertTrue(seconds(refused) > 3000, "about an hour is left");
        assertTrue(
                right(victim, visitor()) instanceof LoginResult.LoggedIn,
                "other visitors are not affected");

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertTrue(
                right(victim, sprayer) instanceof LoginResult.LoggedIn,
                "the limit ends with the hour");
    }

    // A shared network (LOG-13)

    @Test
    void twentyMembersOnOneNetworkAreAllAccepted() {
        String sharedWifi = visitor();
        for (int i = 0; i < 20; i++) {
            UserSummary member = accounts.newUser(Role.MEMBER);
            assertTrue(right(member, sharedWifi) instanceof LoginResult.LoggedIn, "member " + i);
        }
    }

    @Test
    void membersWhoMistypeOnASharedNetworkOnlyWaitForThemselves() {
        String sharedWifi = visitor();
        UserSummary clumsy = accounts.newUser(Role.MEMBER);
        UserSummary careful = accounts.newUser(Role.MEMBER);
        for (int i = 0; i < 3; i++) {
            wrong(clumsy, sharedWifi);
            clock.advance(Duration.ofSeconds(20));
        }
        assertTrue(right(careful, sharedWifi) instanceof LoginResult.LoggedIn);
    }

    // The counters

    @Test
    void theCountersHoldNoEmailAddressAndNoIpAddress() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String address = "198.51.100.23";
        wrong(user, address);
        assertFalse(storage.counterKeys().isEmpty());
        for (String key : storage.counterKeys()) {
            assertFalse(key.contains(user.email()), key);
            assertFalse(key.contains("example.test"), key);
            assertFalse(key.contains("198.51"), key);
            assertTrue(key.length() < 120, "only hashes");
        }
    }

    @Test
    void twoInstancesOfTheApplicationShareOneLimitThroughTheStore() {
        // Two services that use the same store behave as two instances that use the same database
        LoginThrottle other = new LoginThrottle();
        other.store = storage;
        other.fingerprints = fingerprints;
        other.config = config;
        other.clock = clock;

        String email = "shared-" + UUID.randomUUID() + "@example.test";
        String address = visitor();
        throttle.recordFailure(email, address);
        assertTrue(
                other.waitTime(email, address).isPresent(),
                "the second instance sees the first one's failure");
        assertEquals(
                throttle.waitTime(email, address).orElseThrow().toSeconds(),
                other.waitTime(email, address).orElseThrow().toSeconds(),
                "both instances ask for the same wait");
    }

    @Test
    void withoutAVisitorAddressOnlyTheAccountLimitApplies() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        for (int i = 0; i < 20; i++) {
            assertTrue(
                    login.login(user.email(), "wrong-password-xyz") instanceof LoginResult.Failed);
        }
    }
}
