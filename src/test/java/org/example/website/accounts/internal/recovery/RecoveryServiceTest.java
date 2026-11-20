package org.example.website.accounts.internal.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.LogRecord;
import org.example.website.TestAccounts;
import org.example.website.TestLogs;
import org.example.website.accounts.api.LoginResult;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.RecoveryLinkInvalid;
import org.example.website.accounts.api.RecoveryService;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserService;
import org.example.website.accounts.api.UserSummary;
import org.example.website.accounts.internal.security.SessionTokens;
import org.example.website.accounts.internal.store.RecoveryTokenRecord;
import org.example.website.accounts.internal.store.memory.InMemoryStorage;
import org.example.website.platform.TestClock;
import org.example.website.platform.api.Email;
import org.example.website.platform.api.InvalidInput;
import org.example.website.platform.api.Outbox;
import org.example.website.platform.api.ResourceNotFound;
import org.example.website.platform.api.ServiceUnavailable;
import org.example.website.platform.api.TooManyRequests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** USR-4 to USR-6 and USR-16 to USR-24: recovery links, the email and the limits. */
@QuarkusTest
class RecoveryServiceTest {

    @Inject RecoveryService recovery;
    @Inject UserService users;
    @Inject LoginService login;
    @Inject TestAccounts accounts;
    @Inject Outbox outbox;
    @Inject InMemoryStorage storage;
    @Inject TestClock clock;

    @BeforeEach
    void emptyOutbox() {
        outbox.clear();
        outbox.failSends(false);
    }

    @AfterEach
    void reset() {
        clock.reset();
        outbox.failSends(false);
    }

    private UserSummary member() {
        return accounts.newUser(Role.MEMBER);
    }

    private String tokenFor(UserSummary user) {
        recovery.sendTo(user.id());
        return accounts.recoveryToken(user.email());
    }

    // The email (USR-5, USR-6, USR-19, USR-23, DES-11, HRD-1)

    @Test
    void aRequestSendsOneEmailWithALinkOnTheConfiguredAddress() {
        UserSummary user = member();
        recovery.request(user.email(), TestAccounts.randomAddress());

        assertEquals(1, accounts.emailsTo(user.email()));
        Email email = outbox.sent().get(0);
        assertEquals("Choose your password for PLACEHOLDER_NAME", email.subject());
        String token = accounts.recoveryToken(user.email());
        assertNotNull(token);
        assertEquals(43, token.length(), "256 bits");
        // HRD-1: the link starts with the configured address of the website
        assertTrue(email.text().contains(TestAccounts.ORIGIN + "/reset-password?token=" + token));
        assertTrue(email.html().contains(TestAccounts.ORIGIN + "/reset-password?token=" + token));
    }

    @Test
    void theEmailExplainsWhatItIsAndWhatToDoIfYouDidNotAskForIt() {
        UserSummary user = member();
        recovery.sendTo(user.id());
        String text = outbox.sent().get(0).text();
        assertTrue(text.contains("account was created"), "for a new user");
        assertTrue(
                text.contains("asked to reset the password"),
                "and for a forgotten password (USR-23)");
        assertTrue(text.contains("1 hour"));
        assertTrue(text.contains("works once"));
        assertTrue(text.contains("ignore this email"));
        assertFalse(
                text.toLowerCase().contains("your password is"), "no password is ever in the mail");
        assertFalse(text.contains(TestAccounts.PASSWORD));
    }

    @Test
    void theDateOfTheLastEmailIsKept() {
        UserSummary user = member();
        assertNull(users.find(user.id()).orElseThrow().recoveryEmailSentAt());
        Instant before = clock.instant();
        recovery.sendTo(user.id());
        Instant sent = users.find(user.id()).orElseThrow().recoveryEmailSentAt();
        assertNotNull(sent);
        assertFalse(sent.isBefore(before.minusSeconds(1)));
    }

    @Test
    void noEmailIsSentForAnUnknownAddressOrForTheMaster() {
        recovery.request(
                "nobody-" + UUID.randomUUID() + "@example.test", TestAccounts.randomAddress());
        recovery.request(TestAccounts.MASTER_EMAIL, TestAccounts.randomAddress()); // USR-16
        recovery.request("not an address", TestAccounts.randomAddress());
        recovery.request(null, TestAccounts.randomAddress());
        assertEquals(0, outbox.sent().size());
    }

    @Test
    void theAddressIsComparedWithoutRegardToCase() {
        UserSummary user = member();
        recovery.request("  " + user.email().toUpperCase() + " ", TestAccounts.randomAddress());
        assertEquals(1, accounts.emailsTo(user.email()));
    }

    @Test
    void adminsAndMembersCanBeSentAnEmailAgainButTheMasterCannot() {
        UserSummary admin = accounts.newUser(Role.ADMIN);
        recovery.sendTo(admin.id());
        recovery.sendTo(admin.id());
        assertEquals(2, accounts.emailsTo(admin.email()));

        UUID masterId =
                login.login(TestAccounts.MASTER_EMAIL, TestAccounts.MASTER_PASSWORD)
                                instanceof LoginResult.LoggedIn l
                        ? l.user().id()
                        : null;
        assertNotNull(masterId);
        assertThrows(InvalidInput.class, () -> recovery.sendTo(masterId)); // USR-6
        assertThrows(ResourceNotFound.class, () -> recovery.sendTo(UUID.randomUUID()));
        assertEquals(0, accounts.emailsTo(TestAccounts.MASTER_EMAIL));
    }

    @Test
    void aNewUserCanChooseAPasswordWithTheFirstEmail() {
        // USR-4, USR-5: no password until the link is used
        UserSummary user =
                users.createUser("fresh-" + UUID.randomUUID() + "@example.test", Role.MEMBER);
        assertFalse(user.hasPassword());
        String token = tokenFor(user);
        recovery.complete(token, "A-fresh-password-for-a-new-user", TestAccounts.randomAddress());
        assertTrue(
                login.login(user.email(), "A-fresh-password-for-a-new-user")
                        instanceof LoginResult.LoggedIn);
    }

    // The link (USR-19)

    @Test
    void onlyAHashOfTheTokenIsStored() {
        UserSummary user = member();
        String token = tokenFor(user);
        assertTrue(storage.findByHash(token).isEmpty(), "the token itself is not a key");
        assertTrue(storage.findByHash(SessionTokens.hash(token)).isPresent());
    }

    @Test
    void theLinkWorksForExactlyOneHour() {
        UserSummary user = member();
        String token = tokenFor(user);
        clock.advance(Duration.ofMinutes(59).plusSeconds(50));
        assertTrue(recovery.isValid(token, TestAccounts.randomAddress()));
        clock.advance(Duration.ofSeconds(20));
        assertFalse(recovery.isValid(token, TestAccounts.randomAddress()));
        assertThrows(
                RecoveryLinkInvalid.class,
                () ->
                        recovery.complete(
                                token, "A-valid-new-password-1", TestAccounts.randomAddress()));
    }

    @Test
    void theLinkWorksOnlyOnce() {
        UserSummary user = member();
        String token = tokenFor(user);
        recovery.complete(token, "A-valid-new-password-1", TestAccounts.randomAddress());
        assertFalse(recovery.isValid(token, TestAccounts.randomAddress()));
        assertThrows(
                RecoveryLinkInvalid.class,
                () ->
                        recovery.complete(
                                token, "Another-valid-password-2", TestAccounts.randomAddress()));
        // the password of the first use is still the one that works
        assertTrue(
                login.login(user.email(), "A-valid-new-password-1")
                        instanceof LoginResult.LoggedIn);
    }

    @Test
    void aNewRequestMakesOlderLinksOfTheSameUserInvalid() {
        UserSummary user = member();
        String first = tokenFor(user);
        String second = tokenFor(user);
        assertNotEquals(first, second);
        assertFalse(recovery.isValid(first, TestAccounts.randomAddress()));
        assertTrue(recovery.isValid(second, TestAccounts.randomAddress()));
    }

    @Test
    void aLinkOnlyChangesThePasswordOfItsOwnUser() {
        UserSummary a = member();
        UserSummary b = member();
        String tokenA = tokenFor(a);
        tokenFor(b);
        recovery.complete(tokenA, "A-valid-new-password-1", TestAccounts.randomAddress());
        assertTrue(
                login.login(b.email(), TestAccounts.PASSWORD) instanceof LoginResult.LoggedIn,
                "b is untouched");
        assertTrue(login.login(a.email(), TestAccounts.PASSWORD) instanceof LoginResult.Failed);
    }

    @Test
    void aTypoDoesNotUseUpTheLink() {
        UserSummary user = member();
        String token = tokenFor(user);
        assertThrows(
                InvalidInput.class,
                () -> recovery.complete(token, "short", TestAccounts.randomAddress()));
        assertThrows(
                InvalidInput.class,
                () -> recovery.complete(token, "password1234", TestAccounts.randomAddress()));
        assertTrue(recovery.isValid(token, TestAccounts.randomAddress()), "the link still works");
        recovery.complete(token, "A-valid-new-password-1", TestAccounts.randomAddress());
    }

    @Test
    void settingThePasswordEndsAllSessionsOfTheUser() {
        UserSummary user = member();
        String session =
                ((LoginResult.LoggedIn) login.login(user.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        assertTrue(login.userForSession(session).isPresent());
        recovery.complete(
                tokenFor(user), "A-valid-new-password-1", TestAccounts.randomAddress()); // USR-21
        assertTrue(login.userForSession(session).isEmpty());
    }

    @Test
    void theMasterNeverGetsAWorkingLink() {
        // Even a link that was somehow stored for the master does not work (USR-16)
        UUID masterId =
                ((LoginResult.LoggedIn)
                                login.login(
                                        TestAccounts.MASTER_EMAIL, TestAccounts.MASTER_PASSWORD))
                        .user()
                        .id();
        String token = SessionTokens.newToken();
        storage.save(
                new RecoveryTokenRecord(
                        SessionTokens.hash(token),
                        masterId,
                        clock.instant().plus(Duration.ofHours(1))));
        assertFalse(recovery.isValid(token, TestAccounts.randomAddress()));
        assertThrows(
                RecoveryLinkInvalid.class,
                () ->
                        recovery.complete(
                                token, "A-valid-new-password-1", TestAccounts.randomAddress()));
        assertTrue(
                login.login(TestAccounts.MASTER_EMAIL, TestAccounts.MASTER_PASSWORD)
                        instanceof LoginResult.LoggedIn);
    }

    @Test
    void linksOfARemovedUserDoNotWork() {
        UserSummary user = member();
        String token = tokenFor(user);
        storage.delete(user.id());
        assertFalse(recovery.isValid(token, TestAccounts.randomAddress()));
    }

    @Test
    void garbageTokensAreNeverValid() {
        for (String token :
                new String[] {
                    null,
                    "",
                    "x",
                    "garbage",
                    "a".repeat(43),
                    "../../etc/passwd",
                    "' OR 1=1",
                    "a".repeat(1000)
                }) {
            assertFalse(
                    recovery.isValid(token, TestAccounts.randomAddress()), String.valueOf(token));
        }
    }

    // The limits (USR-22, LOG-13)

    @Test
    void atMostThreeEmailsAreSentPerAddressPerHourAndTheRestIsSilent() {
        UserSummary user = member();
        for (int i = 0; i < 6; i++) {
            recovery.request(
                    user.email(), TestAccounts.randomAddress()); // no error: the answer is the same
        }
        assertEquals(3, accounts.emailsTo(user.email()));
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        recovery.request(user.email(), TestAccounts.randomAddress());
        assertEquals(4, accounts.emailsTo(user.email()), "a new hour, a new allowance");
    }

    @Test
    void theLimitPerAddressAppliesToUnknownAddressesInTheSameWay() {
        // Otherwise the limit would show which addresses have an account. Nothing is sent either
        // way.
        String unknown = "nobody-" + UUID.randomUUID() + "@example.test";
        for (int i = 0; i < 6; i++) {
            recovery.request(unknown, TestAccounts.randomAddress());
        }
        assertEquals(0, accounts.emailsTo(unknown));
    }

    @Test
    void aVisitorWhoAsksTooOftenIsRefusedForTheRestOfTheHour() {
        String visitor = TestAccounts.randomAddress();
        for (int i = 0; i < 30; i++) {
            recovery.request("nobody-" + i + "@example.test", visitor);
        }
        TooManyRequests refused =
                assertThrows(
                        TooManyRequests.class,
                        () -> recovery.request("nobody-31@example.test", visitor));
        assertTrue(refused.retryAfterSeconds() > 3000, "about an hour is left");
        recovery.request("nobody-32@example.test", TestAccounts.randomAddress()); // others are fine
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        recovery.request("nobody-33@example.test", visitor);
    }

    @Test
    void aVisitorWhoUsesTooManyWrongLinksIsRefused() {
        String visitor = TestAccounts.randomAddress();
        for (int i = 0; i < 20; i++) {
            assertFalse(recovery.isValid(SessionTokens.newToken(), visitor));
        }
        assertThrows(
                TooManyRequests.class, () -> recovery.isValid(SessionTokens.newToken(), visitor));
        assertThrows(
                TooManyRequests.class,
                () ->
                        recovery.complete(
                                SessionTokens.newToken(), "A-valid-new-password-1", visitor));
        assertFalse(
                recovery.isValid(SessionTokens.newToken(), TestAccounts.randomAddress()),
                "other visitors are not affected");
    }

    @Test
    void aRightLinkIsStillRefusedFromAVisitorWhoIsOverTheLimit() {
        // A guessed token that happens to be right would be found with enough tries, so the limit
        // comes first
        String visitor = TestAccounts.randomAddress();
        for (int i = 0; i < 21; i++) {
            try {
                recovery.isValid(SessionTokens.newToken(), visitor);
            } catch (TooManyRequests expected) {
                // the 21st call
            }
        }
        UserSummary user = member();
        String token = tokenFor(user);
        assertTrue(recovery.isValid(token, TestAccounts.randomAddress()));
    }

    // A failing email service (USR-24, LGG-4)

    @Test
    void aFailingEmailServiceGivesAnErrorToAdminsAndLeavesNoLink() {
        UserSummary user = member();
        outbox.failSends(true);
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        assertThrows(ServiceUnavailable.class, () -> recovery.sendTo(user.id())));
        assertNull(users.find(user.id()).orElseThrow().recoveryEmailSentAt(), "nothing was sent");
        assertTrue(
                logs.stream().anyMatch(r -> TestLogs.everything(r).contains("ALERT")),
                "the failure is logged and alerted");
        for (LogRecord record : logs) {
            assertFalse(TestLogs.everything(record).contains(user.email()));
        }
        outbox.failSends(false);
        assertEquals(0, accounts.emailsTo(user.email()));
    }

    @Test
    void aFailingEmailServiceDoesNotChangeWhatAVisitorSees() {
        UserSummary user = member();
        outbox.failSends(true);
        recovery.request(
                user.email(), TestAccounts.randomAddress()); // no error: the same answer (USR-18)
        recovery.request(
                "nobody-" + UUID.randomUUID() + "@example.test", TestAccounts.randomAddress());
        assertEquals(0, outbox.sent().size());
    }

    // Nothing secret in the logs (LGG-5)

    @Test
    void theTokenTheAddressAndThePasswordAreNeverLogged() {
        UserSummary user = member();
        String newPassword = "Recognizable-new-password-Zq7";
        List<String> tokens = new ArrayList<>();
        List<LogRecord> logs =
                TestLogs.capture(() -> {
                    recovery.request(user.email(), TestAccounts.randomAddress());
                    String token = accounts.recoveryToken(user.email());
                    tokens.add(token);
                    recovery.isValid("wrong-" + token.substring(6), TestAccounts.randomAddress());
                    try {
                        recovery.complete(token, "short", TestAccounts.randomAddress());
                    } catch (InvalidInput expected) {
                        // too short
                    }
                    recovery.complete(token, newPassword, TestAccounts.randomAddress());
                });
        assertFalse(logs.isEmpty());
        for (LogRecord record : logs) {
            String text = TestLogs.everything(record);
            assertFalse(text.contains(tokens.get(0)), "the token is not logged");
            assertFalse(text.contains(SessionTokens.hash(tokens.get(0))));
            assertFalse(text.contains(user.email()), "the address is only logged as a hash");
            assertFalse(text.contains(newPassword));
            assertFalse(text.contains("short"));
        }
        assertTrue(
                logs.stream()
                        .anyMatch(r ->
                                TestLogs.everything(r)
                                        .contains("invalid or expired recovery link")),
                "LGG-4");
    }
}
