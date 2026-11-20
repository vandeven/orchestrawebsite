package org.example.website.accounts.internal.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.logging.LogRecord;
import org.example.website.TestAccounts;
import org.example.website.TestLogs;
import org.example.website.accounts.api.AccountService;
import org.example.website.accounts.api.CurrentPasswordIncorrect;
import org.example.website.accounts.api.LoginResult;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserSummary;
import org.example.website.accounts.internal.security.SessionTokens;
import org.example.website.accounts.internal.store.SessionRepository;
import org.example.website.platform.TestClock;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.platform.api.mail.Email;
import org.example.website.platform.api.mail.Outbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ACC-3 to ACC-8: changing your own password. */
@QuarkusTest
class AccountServiceTest {

    private static final String NEW = "A-brand-new-password-for-me-1";

    @Inject AccountService account;
    @Inject LoginService login;
    @Inject TestAccounts accounts;
    @Inject Outbox outbox;
    @Inject SessionRepository sessions;
    @Inject TestClock clock;

    @BeforeEach
    void clean() {
        outbox.clear();
        outbox.failSends(false);
    }

    @AfterEach
    void reset() {
        clock.reset();
        outbox.failSends(false);
    }

    private String sessionOf(UserSummary user) {
        return ((LoginResult.LoggedIn) login.login(user.email(), TestAccounts.PASSWORD))
                .sessionToken();
    }

    private static String visitor() {
        return TestAccounts.randomAddress();
    }

    // The change (ACC-3, ACC-6)

    @Test
    void theNewPasswordWorksAndTheOldOneDoesNot() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        account.changePassword(sessionOf(user), TestAccounts.PASSWORD, NEW, visitor());
        assertTrue(login.login(user.email(), TestAccounts.PASSWORD) instanceof LoginResult.Failed);
        assertTrue(login.login(user.email(), NEW) instanceof LoginResult.LoggedIn);
    }

    @Test
    void everyOtherSessionEndsAndThisDeviceGetsANewToken() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String thisDevice = sessionOf(user);
        String phone = sessionOf(user);
        String lostLaptop = sessionOf(user);
        String someoneElse = accounts.sessionToken(Role.MEMBER);

        String newToken = account.changePassword(thisDevice, TestAccounts.PASSWORD, NEW, visitor());

        assertNotEquals(thisDevice, newToken);
        assertTrue(login.userForSession(newToken).isPresent(), "this device stays logged in");
        assertEquals(user.id(), login.userForSession(newToken).orElseThrow().id());
        assertTrue(
                login.userForSession(thisDevice).isEmpty(), "the old token of this device is dead");
        assertTrue(login.userForSession(phone).isEmpty());
        assertTrue(login.userForSession(lostLaptop).isEmpty());
        assertTrue(login.userForSession(someoneElse).isPresent(), "other users are not touched");
    }

    @Test
    void theNewSessionLastsAsLongAsTheOldOneWould() {
        // A password change does not extend a login beyond its day (LOG-10)
        UserSummary user = accounts.newUser(Role.MEMBER);
        String old = sessionOf(user);
        var oldExpiry = sessions.findByTokenHash(SessionTokens.hash(old)).orElseThrow().expiresAt();
        clock.advance(Duration.ofHours(10));
        String fresh = account.changePassword(old, TestAccounts.PASSWORD, NEW, visitor());
        assertEquals(
                oldExpiry,
                sessions.findByTokenHash(SessionTokens.hash(fresh)).orElseThrow().expiresAt());
        clock.advance(Duration.ofHours(14).plusMinutes(1));
        assertTrue(login.userForSession(fresh).isEmpty());
    }

    @Test
    void adminsCanChangeTheirPasswordToo() {
        UserSummary admin = accounts.newUser(Role.ADMIN);
        account.changePassword(sessionOf(admin), TestAccounts.PASSWORD, NEW, visitor());
        assertTrue(login.login(admin.email(), NEW) instanceof LoginResult.LoggedIn);
    }

    // The email (ACC-7)

    @Test
    void oneEmailIsSentWithTheTimeInAmsterdamAndNoLinkOrPassword() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        account.changePassword(sessionOf(user), TestAccounts.PASSWORD, NEW, visitor());

        assertEquals(1, accounts.emailsTo(user.email()));
        Email email = outbox.sent().get(0);
        assertEquals("Your password for PLACEHOLDER_NAME was changed", email.subject());
        for (String body : List.of(email.text(), email.html())) {
            assertTrue(body.contains("(Amsterdam time)"));
            assertTrue(
                    body.matches("(?s).*[A-Z][a-z]+ \\d{1,2} [A-Z][a-z]+ \\d{4}, \\d{2}:\\d{2}.*"),
                    "the time of the change");
            assertTrue(body.contains("If it was not you, contact an admin"));
            assertFalse(body.contains(NEW));
            assertFalse(body.contains(TestAccounts.PASSWORD));
            assertFalse(body.contains("/reset-password"), "no link");
        }
        assertFalse(email.text().contains("http"), "no link at all");
        assertEquals(0, org.jsoup.Jsoup.parse(email.html()).select("a[href]").size());
    }

    @Test
    void aFailedEmailDoesNotUndoTheChangeButIsLogged() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String session = sessionOf(user);
        outbox.failSends(true);
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        account.changePassword(session, TestAccounts.PASSWORD, NEW, visitor()));
        assertTrue(
                login.login(user.email(), NEW) instanceof LoginResult.LoggedIn,
                "the password was changed");
        assertTrue(
                logs.stream().anyMatch(r -> TestLogs.everything(r).contains("ALERT")),
                "and the failure is logged");
        for (LogRecord record : logs) {
            assertFalse(TestLogs.everything(record).contains(user.email()));
        }
    }

    // A wrong current password (ACC-4)

    @Test
    void aWrongCurrentPasswordChangesNothing() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String session = sessionOf(user);
        assertThrows(
                CurrentPasswordIncorrect.class,
                () -> account.changePassword(session, "not-my-password-1", NEW, visitor()));
        assertThrows(
                CurrentPasswordIncorrect.class,
                () -> account.changePassword(session, "", NEW, visitor()));
        assertThrows(
                CurrentPasswordIncorrect.class,
                () -> account.changePassword(session, null, NEW, visitor()));
        assertThrows(
                CurrentPasswordIncorrect.class,
                () -> account.changePassword(session, "x".repeat(500), NEW, visitor()));
        assertTrue(login.userForSession(session).isPresent(), "the session is untouched");
        assertTrue(
                login.login(user.email(), TestAccounts.PASSWORD) instanceof LoginResult.LoggedIn,
                "the password is the same");
        assertEquals(0, accounts.emailsTo(user.email()), "no email");
    }

    @Test
    void wrongAttemptsAreSlowedDownLikeLogins() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String session = sessionOf(user);
        String address = visitor();
        assertThrows(
                CurrentPasswordIncorrect.class,
                () -> account.changePassword(session, "wrong-one-xyz", NEW, address));
        TooManyRequests wait =
                assertThrows(
                        TooManyRequests.class,
                        () -> account.changePassword(session, TestAccounts.PASSWORD, NEW, address));
        assertEquals(
                1,
                wait.retryAfterSeconds(),
                "even the right current password is refused during the wait");
        clock.advance(Duration.ofSeconds(2));
        account.changePassword(session, TestAccounts.PASSWORD, NEW, address);
        assertTrue(login.login(user.email(), NEW) instanceof LoginResult.LoggedIn);
    }

    @Test
    void theWaitIsSharedBetweenLoginsAndPasswordChanges() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String session = sessionOf(user);
        String address = visitor();
        // a failed change makes a login from the same visitor wait
        assertThrows(
                CurrentPasswordIncorrect.class,
                () -> account.changePassword(session, "wrong-one-xyz", NEW, address));
        assertTrue(
                login.login(user.email(), TestAccounts.PASSWORD, address)
                        instanceof LoginResult.Throttled);
        clock.advance(Duration.ofSeconds(2));
        // and a failed login makes a change wait
        assertTrue(
                login.login(user.email(), "wrong-again-xyz", address)
                        instanceof LoginResult.Failed);
        assertThrows(
                TooManyRequests.class,
                () -> account.changePassword(session, TestAccounts.PASSWORD, NEW, address));
    }

    @Test
    void anotherVisitorIsNotSlowedDown() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String session = sessionOf(user);
        assertThrows(
                CurrentPasswordIncorrect.class,
                () -> account.changePassword(session, "wrong-one-xyz", NEW, visitor()));
        account.changePassword(session, TestAccounts.PASSWORD, NEW, visitor());
    }

    // The rules for a new password (ACC-5)

    @Test
    void lengthBoundary() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String session = sessionOf(user);
        assertThrows(
                InvalidInput.class,
                () ->
                        account.changePassword(
                                session, TestAccounts.PASSWORD, "aB3$xY9!qW7", visitor())); // 11
        assertThrows(
                InvalidInput.class,
                () ->
                        account.changePassword(
                                session,
                                TestAccounts.PASSWORD,
                                ("aB3$xY9!qW7z".repeat(11)).substring(0, 129),
                                visitor()));
        assertThrows(
                InvalidInput.class,
                () -> account.changePassword(session, TestAccounts.PASSWORD, null, visitor()));
        assertTrue(
                login.userForSession(session).isPresent(),
                "refused changes do not end the session");
        String long128 = ("aB3$xY9!qW7z".repeat(11)).substring(0, 128);
        String fresh = account.changePassword(session, TestAccounts.PASSWORD, long128, visitor());
        String twelve = "aB3$xY9!qW7z";
        account.changePassword(fresh, long128, twelve, visitor());
        assertTrue(login.login(user.email(), twelve) instanceof LoginResult.LoggedIn);
    }

    @Test
    void aCommonPasswordAndTheSamePasswordAreRefused() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String session = sessionOf(user);
        assertThrows(
                InvalidInput.class,
                () ->
                        account.changePassword(
                                session, TestAccounts.PASSWORD, "password1234", visitor()));
        assertThrows(
                InvalidInput.class,
                () ->
                        account.changePassword(
                                session, TestAccounts.PASSWORD, "a".repeat(20), visitor()));
        assertThrows(
                InvalidInput.class,
                () ->
                        account.changePassword(
                                session, TestAccounts.PASSWORD, TestAccounts.PASSWORD, visitor()),
                "the same as the current one");
        assertTrue(
                login.login(user.email(), TestAccounts.PASSWORD) instanceof LoginResult.LoggedIn);
    }

    // The master and visitors (ACC-8)

    @Test
    void theMasterCannotChangeThePasswordHere() {
        String master = accounts.sessionToken(Role.MASTER);
        assertThrows(
                AccessDenied.class,
                () -> account.changePassword(master, TestAccounts.MASTER_PASSWORD, NEW, visitor()));
        assertTrue(
                login.login(TestAccounts.MASTER_EMAIL, TestAccounts.MASTER_PASSWORD)
                        instanceof LoginResult.LoggedIn);
        assertEquals(0, accounts.emailsTo(TestAccounts.MASTER_EMAIL));
    }

    @Test
    void aSessionThatIsNotValidIsRefused() {
        for (String token : new String[] {null, "", "garbage", SessionTokens.newToken()}) {
            assertThrows(
                    AccessDenied.class,
                    () -> account.changePassword(token, "x", NEW, visitor()),
                    String.valueOf(token));
        }
        UserSummary user = accounts.newUser(Role.MEMBER);
        String ended = sessionOf(user);
        login.logout(ended);
        assertThrows(
                AccessDenied.class,
                () -> account.changePassword(ended, TestAccounts.PASSWORD, NEW, visitor()));
    }

    // Nothing secret in the logs (ACC-9, LGG-5)

    @Test
    void thePasswordsAndTheAddressAreNeverLogged() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String session = sessionOf(user);
        String typo = "Recognizable-wrong-password-Zq7";
        String fresh = "Recognizable-new-password-Mn5";
        List<LogRecord> logs =
                TestLogs.capture(() -> {
                    try {
                        account.changePassword(session, typo, fresh, visitor());
                    } catch (CurrentPasswordIncorrect expected) {
                        // wrong current password
                    }
                    clock.advance(Duration.ofSeconds(5));
                    account.changePassword(session, TestAccounts.PASSWORD, fresh, visitor());
                });
        assertFalse(logs.isEmpty());
        for (LogRecord record : logs) {
            String text = TestLogs.everything(record);
            assertFalse(text.contains(typo));
            assertFalse(text.contains(fresh));
            assertFalse(text.contains(TestAccounts.PASSWORD));
            assertFalse(text.contains(user.email()));
        }
        assertTrue(
                logs.stream()
                        .anyMatch(r -> TestLogs.everything(r).contains("a password was changed")),
                "LGG-4: a password change is a security event");
    }
}
