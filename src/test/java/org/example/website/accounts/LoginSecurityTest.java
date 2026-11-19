package org.example.website.accounts;

import static io.restassured.RestAssured.given;
import static org.example.website.accounts.LoginTest.login;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.LogRecord;
import org.example.website.TestAccounts;
import org.example.website.TestLogs;
import org.example.website.accounts.api.LoginResult;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserService;
import org.example.website.accounts.api.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The attacks of login.md: injection, odd input, headers, logs, cross-site requests and timing. */
@QuarkusTest
class LoginSecurityTest {

    @Inject TestAccounts accounts;
    @Inject LoginService loginService;
    @Inject UserService users;

    // Injection and unusual input

    @ParameterizedTest
    @ValueSource(
            strings = {
                "' OR '1'='1",
                "admin'--",
                "\" OR \"\"=\"",
                "1; DROP TABLE users",
                "{\"$ne\": null}",
                "{$gt: ''}",
                "[$ne]=1",
                "<script>alert('x')</script>",
                "<img src=x onerror=alert(1)>",
                "${7*7}",
                "{{7*7}}",
                "#{7*7}",
                "a@example.test\r\nBcc: attacker@example.org",
                "a@example.test\u0000",
                "ａ＠ｅｘａｍｐｌｅ．ｔｅｓｔ",
                "a@example.test‮",
                "../../etc/passwd",
                "%00",
                "%0d%0aSet-Cookie: x=1"
            })
    void unusualInputIsPlainTextAndNeverBreaksAnything(String value) {
        for (String[] pair :
                new String[][] {{value, "password-123456"}, {"a@example.test", value}}) {
            Response response = login(pair[0], pair[1]);
            assertTrue(
                    response.statusCode() == 200 || response.statusCode() == 400,
                    "status " + response.statusCode() + " for " + Arrays.toString(pair));
            assertFalse(response.headers().hasHeaderWithName("Set-Cookie"));
            String body = response.asString();
            assertFalse(body.contains("Exception"), "no error details");
            assertFalse(body.contains("<script>alert"), "input is never shown unescaped");
            assertFalse(body.contains("<img src=x"), "input is never shown unescaped");
            assertFalse(body.contains("at org."), "no stack trace");
            assertTrue(body.contains("Log in"), "the login page again");
            assertEquals(null, response.header("Bcc"));
        }
    }

    @Test
    void veryLongValuesAreRefusedCleanly() {
        // Quarkus refuses a form field above its size limit (2 KB) before the page sees it. The
        // visitor gets the friendly error page, with no details and no session.
        Response response = login("a".repeat(100_000) + "@example.test", "b".repeat(100_000));
        assertEquals(413, response.statusCode());
        assertFalse(response.headers().hasHeaderWithName("Set-Cookie"));
        response.then()
                .body(not(containsString("Exception")))
                .body(not(containsString("at org.")))
                .header("Content-Security-Policy", containsString("default-src 'self'"));
        // Just above what the rules allow but below that limit, the page explains it
        assertEquals(400, login("a".repeat(300) + "@example.test", "b").statusCode());
    }

    @Test
    void duplicateAndExtraFieldsDoNothingSurprising() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        Response response =
                given().redirects()
                        .follow(false)
                        .header("Origin", TestAccounts.ORIGIN)
                        .formParam("email", user.email(), "other@example.test")
                        .formParam("password", TestAccounts.PASSWORD)
                        .formParam("role", "MASTER")
                        .formParam("admin", "true")
                        .post("/login");
        // Whatever happens, the extra fields never change the role
        if (response.statusCode() == 303) {
            String token = response.header("Set-Cookie").split(";")[0].split("=")[1];
            var found = loginService.userForSession(token);
            assertEquals(Role.MEMBER, found.orElseThrow().role());
        }
    }

    // Response headers (LOG-12, compare SEC-13)

    @Test
    void theLoginPageSendsTheStrictHeaders() {
        for (Response response : List.of(given().get("/login"), login("x@example.test", "wrong"))) {
            response.then()
                    .header("Cache-Control", "no-store")
                    .header("Content-Security-Policy", containsString("script-src 'self'"))
                    .header("Content-Security-Policy", containsString("frame-ancestors 'none'"))
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Referrer-Policy", "strict-origin-when-cross-origin");
        }
    }

    // Nothing secret in the logs or responses (LOG-15, LOG-21, LGG-5)

    @Test
    void thePasswordIsNeverLoggedOrReturned() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String secretLooking = "Zx9-very-recognizable-password-Qw4";
        List<Response> responses = new ArrayList<>();
        List<LogRecord> logs =
                TestLogs.capture(() -> {
                    responses.add(login(user.email(), secretLooking)); // fails
                    responses.add(login("unknown@example.test", secretLooking));
                    responses.add(login(user.email(), TestAccounts.PASSWORD)); // works
                    responses.add(login(user.email(), "x".repeat(200)));
                });
        assertFalse(logs.isEmpty(), "the failed logins are logged");
        for (LogRecord record : logs) {
            String text = TestLogs.everything(record);
            assertFalse(text.contains(secretLooking), "the password is not in the log");
            assertFalse(text.contains(TestAccounts.PASSWORD), "the password is not in the log");
            assertFalse(
                    text.contains(user.email()), "the address is only logged as a hash (LGG-5)");
            assertFalse(text.contains("unknown@example.test"));
        }
        for (Response response : responses) {
            assertFalse(response.asString().contains(secretLooking));
            assertFalse(response.asString().contains(TestAccounts.PASSWORD));
            assertFalse(response.headers().toString().contains(TestAccounts.PASSWORD));
        }
    }

    @Test
    void aFailedLoginIsLoggedAsASecurityEventWithoutTheAddress() {
        List<LogRecord> logs =
                TestLogs.capture(() -> login("someone@example.test", "wrong-password-1"));
        assertTrue(
                logs.stream().anyMatch(r -> TestLogs.everything(r).contains("Failed login")),
                "LGG-4: failed logins are logged");
    }

    @Test
    void theStoredUserDataHasNoPlainPasswordOrToken() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        // The summary other components see never has the hash at all
        assertFalse(user.toString().contains(TestAccounts.PASSWORD));
        assertTrue(user.hasPassword());
        assertFalse(user.toString().toLowerCase().contains("argon2"));
    }

    // Account enumeration: the work done is the same (LOG-13)

    @Test
    void unknownAndKnownAddressesTakeAboutTheSameTime() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        UserSummary noPassword =
                users.createUser("np-" + System.nanoTime() + "@example.test", Role.MEMBER);
        // warm up
        loginService.login(user.email(), "warm-up-password");
        loginService.login("warm@example.test", "warm-up-password");

        long known = fastest(() -> loginService.login(user.email(), "wrong-password-1"));
        long unknown =
                fastest(() ->
                        loginService.login(
                                "nobody-" + System.nanoTime() + "@example.test",
                                "wrong-password-1"));
        long noPass = fastest(() -> loginService.login(noPassword.email(), "wrong-password-1"));

        // Both do one full password hash, so the fastest runs are in the same range. The margin is
        // generous on purpose: the point is that skipping the hash, which would be about 100 times
        // faster, is noticed.
        assertTrue(
                unknown > known / 3 && unknown < known * 3,
                "known=" + known + "us unknown=" + unknown + "us");
        assertTrue(
                noPass > known / 3 && noPass < known * 3,
                "known=" + known + "us noPassword=" + noPass + "us");
    }

    /**
     * The fastest of several runs, in microseconds. The fastest run measures the work itself;
     * slower runs only measure how busy the machine was, which would make this test flaky.
     */
    private static long fastest(Runnable action) {
        long fastest = Long.MAX_VALUE;
        for (int i = 0; i < 15; i++) {
            long start = System.nanoTime();
            action.run();
            fastest = Math.min(fastest, (System.nanoTime() - start) / 1_000);
        }
        return fastest;
    }

    @Test
    void aMalformedRequestStillDoesThePasswordWork() {
        // A request that cannot be a real login is not cheaper than one that can
        loginService.login("warm@example.test", "warm-up-password");
        long normal = fastest(() -> loginService.login("nobody@example.test", "wrong-password-1"));
        long malformed = fastest(() -> loginService.login("not an address", "wrong-password-1"));
        assertTrue(malformed > normal / 3, "malformed=" + malformed + "us normal=" + normal + "us");
    }

    // CSRF: requests that do not come from this website (LOG-14)

    @Test
    void aLoginFromAnotherSiteIsRefused() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        for (String origin :
                new String[] {
                    "https://evil.example",
                    "http://localhost:8082",
                    "http://localhost",
                    "https://localhost:8081",
                    "null",
                    ""
                }) {
            Response response =
                    given().redirects()
                            .follow(false)
                            .accept("text/html")
                            .header("Origin", origin)
                            .formParam("email", user.email())
                            .formParam("password", TestAccounts.PASSWORD)
                            .post("/login");
            assertEquals(403, response.statusCode(), "origin: '" + origin + "'");
            assertFalse(response.headers().hasHeaderWithName("Set-Cookie"));
        }
    }

    @Test
    void aLoginWithoutAnyOriginInformationIsRefused() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        given().redirects()
                .follow(false)
                .accept("text/html")
                .formParam("email", user.email())
                .formParam("password", TestAccounts.PASSWORD)
                .post("/login")
                .then()
                .statusCode(403)
                .body(containsString("Access denied"));
    }

    @Test
    void theFetchMetadataHeaderIsAnAlternativeToOrigin() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        given().redirects()
                .follow(false)
                .header("Sec-Fetch-Site", "same-origin")
                .formParam("email", user.email())
                .formParam("password", TestAccounts.PASSWORD)
                .post("/login")
                .then()
                .statusCode(303);
        for (String site : new String[] {"cross-site", "same-site", "none"}) {
            given().redirects()
                    .follow(false)
                    .header("Sec-Fetch-Site", site)
                    .formParam("email", user.email())
                    .formParam("password", TestAccounts.PASSWORD)
                    .post("/login")
                    .then()
                    .statusCode(403);
        }
    }

    @Test
    void aLogoutFromAnotherSiteDoesNotEndTheSession() {
        String cookie = accounts.cookie(Role.MEMBER);
        given().redirects()
                .follow(false)
                .header("Origin", "https://evil.example")
                .header("Cookie", cookie)
                .post("/logout")
                .then()
                .statusCode(403);
        given().redirects()
                .follow(false)
                .header("Cookie", cookie)
                .get("/members")
                .then()
                .statusCode(200);
    }

    @Test
    void readingPagesNeverNeedAnOrigin() {
        given().get("/login").then().statusCode(200);
        given().get("/").then().statusCode(200);
        given().head("/").then().statusCode(200);
    }

    @Test
    void aRefusedCrossSiteRequestIsLoggedWithoutTheValues() {
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        given().header("Origin", "https://secret-evil-host.example")
                                .formParam("email", "x@example.test")
                                .post("/login"));
        assertTrue(
                logs.stream()
                        .anyMatch(r ->
                                TestLogs.everything(r)
                                        .contains("does not come from this website")));
        for (LogRecord record : logs) {
            assertFalse(TestLogs.everything(record).contains("secret-evil-host"), "LGG-18");
        }
    }

    // Not possible without a password

    @Test
    void anAccountWithoutAPasswordCannotLogInWithAnything() {
        UserSummary user =
                users.createUser("fresh-" + System.nanoTime() + "@example.test", Role.MEMBER);
        assertFalse(user.hasPassword());
        for (String guess :
                new String[] {"", "password", "null", TestAccounts.PASSWORD, "x".repeat(20)}) {
            assertTrue(
                    loginService.login(user.email(), guess) instanceof LoginResult.Failed, guess);
        }
    }
}
