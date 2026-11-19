package org.example.website.accounts;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.LogRecord;
import org.example.website.TestAccounts;
import org.example.website.TestLogs;
import org.example.website.accounts.api.LoginResult;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.RecoveryService;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserSummary;
import org.example.website.platform.TestClock;
import org.example.website.platform.api.Outbox;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * USR-16 to USR-23 and HRD-1, HRD-2 through the pages: asking, opening the link, choosing a
 * password.
 */
@QuarkusTest
class RecoveryWebTest {

    private static final String GOOD = "A-brand-new-password-for-the-test";

    @Inject TestAccounts accounts;
    @Inject Outbox outbox;
    @Inject RecoveryService recovery;
    @Inject LoginService login;
    @Inject TestClock clock;

    @BeforeEach
    void emptyOutbox() {
        outbox.clear();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private static RequestSpecification post() {
        return given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Origin", TestAccounts.ORIGIN)
                .header("X-Forwarded-For", TestAccounts.randomAddress())
                .contentType("application/x-www-form-urlencoded");
    }

    private static Response ask(String email) {
        return post().formParam("email", email).post("/forgot-password");
    }

    /** Opens the link from the email and returns the cookie header value for the next request. */
    private static String openLink(String token) {
        Response response =
                given().redirects()
                        .follow(false)
                        .header("X-Forwarded-For", TestAccounts.randomAddress())
                        .queryParam("token", token)
                        .get("/reset-password");
        assertEquals(303, response.statusCode());
        return response.header("Set-Cookie").split(";")[0];
    }

    private static Response choose(String cookie, String password, String again) {
        return post().header("Cookie", cookie)
                .formParam("password", password)
                .formParam("password2", again)
                .post("/set-password");
    }

    // Asking for an email (USR-17, USR-18)

    @Test
    void theRequestPageHasTheFormAndALinkBack() {
        Response response = given().accept("text/html").get("/forgot-password");
        response.then()
                .statusCode(200)
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer");
        Document page = Jsoup.parse(response.asString());
        assertEquals("Forgot password", page.selectFirst("h1").text());
        assertEquals("email", page.selectFirst("input[name=email]").attr("type"));
        assertEquals("254", page.selectFirst("input[name=email]").attr("maxlength"));
        assertNotNull(page.selectFirst("label[for=email]"));
        assertEquals("/login", page.selectFirst("a:contains(Back to log in)").attr("href"));
    }

    @Test
    void theLoginPageLinksToIt() {
        // LOG-8
        assertEquals(
                "/forgot-password",
                Jsoup.parse(given().get("/login").asString())
                        .selectFirst("a:contains(Forgot password?)")
                        .attr("href"));
        given().get("/forgot-password").then().statusCode(200);
    }

    @Test
    void everyAddressGetsExactlyTheSameAnswer() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        String unknown = "nobody-" + UUID.randomUUID() + "@example.test";
        Response known = ask(member.email());
        Response missing = ask(unknown);
        Response master = ask(TestAccounts.MASTER_EMAIL);

        for (Response response : List.of(known, missing, master)) {
            assertEquals(200, response.statusCode());
            assertEquals("no-store", response.header("Cache-Control"));
            assertTrue(
                    Jsoup.parse(response.asString())
                            .selectFirst("[role=status]")
                            .text()
                            .contains(
                                    "If an account exists for this address, we have sent an email"));
        }
        assertEquals(
                known.asString(),
                missing.asString(),
                "the page does not show whether the account exists");
        assertEquals(known.asString(), master.asString(), "nor whether it is the master");
        assertEquals(1, accounts.emailsTo(member.email()));
        assertEquals(0, accounts.emailsTo(unknown));
        assertEquals(0, accounts.emailsTo(TestAccounts.MASTER_EMAIL));
    }

    @Test
    void formatErrorsAreShownNextToTheField() {
        Response empty = ask("");
        assertEquals(400, empty.statusCode());
        assertEquals(
                "Enter your email address",
                Jsoup.parse(empty.asString()).selectFirst("#email-error").text());
        Response bad = ask("not-an-address");
        assertEquals(400, bad.statusCode());
        Document page = Jsoup.parse(bad.asString());
        assertEquals("Enter a valid email address", page.selectFirst("#email-error").text());
        assertEquals("not-an-address", page.selectFirst("input[name=email]").val());
        assertEquals("true", page.selectFirst("#email").attr("aria-invalid"));
        assertEquals(0, outbox.sent().size());
    }

    @Test
    void aVisitorWhoAsksTooOftenGetsA429() {
        String visitor = TestAccounts.randomAddress();
        for (int i = 0; i < 30; i++) {
            given().redirects()
                    .follow(false)
                    .header("Origin", TestAccounts.ORIGIN)
                    .header("X-Forwarded-For", visitor)
                    .formParam("email", "nobody-" + i + "@example.test")
                    .post("/forgot-password")
                    .then()
                    .statusCode(200);
        }
        Response refused =
                given().redirects()
                        .follow(false)
                        .accept("text/html")
                        .header("Origin", TestAccounts.ORIGIN)
                        .header("X-Forwarded-For", visitor)
                        .formParam("email", "nobody-31@example.test")
                        .post("/forgot-password");
        assertEquals(429, refused.statusCode());
        assertNotNull(refused.header("Retry-After"));
        assertTrue(refused.asString().contains("Too many attempts"));
        assertFalse(refused.asString().contains("Exception"));
    }

    @Test
    void theRequestIsRefusedFromAnotherSite() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Origin", "https://evil.example")
                .formParam("email", "a@example.test")
                .post("/forgot-password")
                .then()
                .statusCode(403);
        given().redirects()
                .follow(false)
                .accept("text/html")
                .formParam("email", "a@example.test")
                .post("/forgot-password")
                .then()
                .statusCode(403);
        assertEquals(0, outbox.sent().size());
    }

    // The link points to the configured address, never to the Host header (HRD-1)

    @Test
    void aForgedHostHeaderDoesNotChangeTheLinkInTheEmail() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        post().header("X-Forwarded-Host", "evil.example")
                .header("Forwarded", "host=evil.example;proto=https")
                .formParam("email", member.email())
                .post("/forgot-password")
                .then()
                .statusCode(200);
        String text = outbox.sent().get(0).text() + outbox.sent().get(0).html();
        assertFalse(text.contains("evil.example"));
        assertTrue(text.contains(TestAccounts.ORIGIN + "/reset-password?token="));
    }

    // Opening the link (USR-19, HRD-2)

    @Test
    void openingTheLinkMovesTheTokenToACookieAndRedirectsWithoutItInTheAddress() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        recovery.sendTo(member.id());
        String token = accounts.recoveryToken(member.email());

        Response response =
                given().redirects()
                        .follow(false)
                        .header("X-Forwarded-For", TestAccounts.randomAddress())
                        .queryParam("token", token)
                        .get("/reset-password");
        response.then()
                .statusCode(303)
                .header("Location", "/set-password")
                .header("Referrer-Policy", "no-referrer")
                .header("Cache-Control", "no-store");
        assertFalse(response.header("Location").contains(token), "the token is not in the address");
        assertEquals("", response.asString(), "and not in a body either");

        String cookie = response.header("Set-Cookie");
        assertTrue(
                cookie.startsWith("__Host-recovery=" + token + ";"),
                "the only place the token goes");
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(
                cookie.contains("SameSite=Lax"),
                "it must come back on the redirect from the email");
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Max-Age=900"));
        assertFalse(cookie.contains("Domain"));
    }

    @Test
    void aWrongExpiredOrUsedLinkGetsTheSameFriendlyPage() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        recovery.sendTo(member.id());
        String expired = accounts.recoveryToken(member.email());
        clock.advance(Duration.ofHours(2));

        String pageForWrong = linkPage("x".repeat(43));
        String pageForGarbage = linkPage("garbage");
        String pageForExpired = linkPage(expired);
        assertEquals(pageForWrong, pageForGarbage);
        assertEquals(pageForWrong, pageForExpired, "an expired link looks like a wrong one");
        assertTrue(pageForWrong.contains("This link does not work"));
        assertTrue(pageForWrong.contains("/forgot-password"));
        assertFalse(pageForWrong.contains("Exception"));
    }

    private static String linkPage(String token) {
        Response response =
                given().redirects()
                        .follow(false)
                        .accept("text/html")
                        .header("X-Forwarded-For", TestAccounts.randomAddress())
                        .queryParam("token", token)
                        .get("/reset-password");
        assertEquals(400, response.statusCode());
        assertEquals("no-referrer", response.header("Referrer-Policy"));
        assertFalse(response.headers().hasHeaderWithName("Set-Cookie"));
        return response.asString();
    }

    @Test
    void theSetPasswordPageNeedsTheCookie() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .get("/set-password")
                .then()
                .statusCode(400);
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Cookie", "__Host-recovery=garbage")
                .get("/set-password")
                .then()
                .statusCode(400);
        post().formParam("password", GOOD)
                .formParam("password2", GOOD)
                .post("/set-password")
                .then()
                .statusCode(400);
    }

    // Choosing a password (USR-20, USR-21, LOG-22)

    @Test
    void thePageHasTheFieldsForANewPassword() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        recovery.sendTo(member.id());
        String cookie = openLink(accounts.recoveryToken(member.email()));

        Response response = given().header("Cookie", cookie).get("/set-password");
        response.then()
                .statusCode(200)
                .header("Referrer-Policy", "no-referrer")
                .header("Cache-Control", "no-store");
        Document page = Jsoup.parse(response.asString());
        for (String name : List.of("password", "password2")) {
            var field = page.selectFirst("input[name=" + name + "]");
            assertEquals("password", field.attr("type"));
            assertEquals("new-password", field.attr("autocomplete"));
            assertEquals("12", field.attr("minlength"));
            assertEquals("128", field.attr("maxlength"));
            assertTrue(field.hasAttr("required"));
            assertNotNull(page.selectFirst("label[for=" + field.id() + "]"));
        }
        assertTrue(page.selectFirst("[data-toggle-password]").hasAttr("hidden"));
        assertTrue(
                page.select("a[href^=http], img[src^=http], script[src^=http]").isEmpty(),
                "loads nothing from other sites");
        assertFalse(
                response.asString().contains(accounts.recoveryToken(member.email())),
                "the token is not in the page");
    }

    @Test
    void theRulesAreShownNextToTheFieldsAndTheLinkSurvivesAMistake() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        recovery.sendTo(member.id());
        String cookie = openLink(accounts.recoveryToken(member.email()));

        Response tooShort = choose(cookie, "short", "short");
        assertEquals(400, tooShort.statusCode());
        assertEquals(
                "Use at least 12 characters",
                Jsoup.parse(tooShort.asString()).selectFirst("#password-error").text());
        assertEquals(
                "true",
                Jsoup.parse(tooShort.asString()).selectFirst("#password").attr("aria-invalid"));
        assertEquals(
                "",
                Jsoup.parse(tooShort.asString()).selectFirst("#password").val(),
                "a password is never shown again");

        Response tooLong = choose(cookie, "x".repeat(129), "x".repeat(129));
        assertEquals(400, tooLong.statusCode());
        assertEquals(
                "Use at most 128 characters",
                Jsoup.parse(tooLong.asString()).selectFirst("#password-error").text());

        Response common = choose(cookie, "password1234", "password1234");
        assertEquals(400, common.statusCode());
        assertEquals(
                "That password is too common, choose another one",
                Jsoup.parse(common.asString()).selectFirst("#password-error").text());

        Response mismatch = choose(cookie, GOOD, GOOD + "x");
        assertEquals(400, mismatch.statusCode());
        assertEquals(
                "The passwords are not the same",
                Jsoup.parse(mismatch.asString()).selectFirst("#password2-error").text());

        // After all those mistakes the link still works
        assertEquals(303, choose(cookie, GOOD, GOOD).statusCode());
    }

    @Test
    void thePasswordLengthBoundaryIs12And128() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        recovery.sendTo(member.id());
        String cookie = openLink(accounts.recoveryToken(member.email()));
        assertEquals(400, choose(cookie, "aB3$xY9!qW7", "aB3$xY9!qW7").statusCode()); // 11
        String twelve = "aB3$xY9!qW7z";
        assertEquals(303, choose(cookie, twelve, twelve).statusCode()); // 12
    }

    @Test
    void choosingAPasswordSetsItEndsSessionsClearsTheCookieAndGoesToTheLoginPage() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        String session =
                ((LoginResult.LoggedIn) login.login(member.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        recovery.sendTo(member.id());
        String token = accounts.recoveryToken(member.email());
        String cookie = openLink(token);

        Response done = choose(cookie, GOOD, GOOD);
        done.then()
                .statusCode(303)
                .header("Location", "/login?notice=password-set")
                .header("Referrer-Policy", "no-referrer");
        assertFalse(done.header("Location").contains(token));
        String cleared = done.header("Set-Cookie");
        assertTrue(
                cleared.startsWith("__Host-recovery=;") && cleared.contains("Max-Age=0"), cleared);

        assertTrue(login.userForSession(session).isEmpty(), "all sessions ended (USR-21)");
        assertTrue(
                login.login(member.email(), TestAccounts.PASSWORD) instanceof LoginResult.Failed,
                "the old password is gone");
        assertTrue(login.login(member.email(), GOOD) instanceof LoginResult.LoggedIn);

        // The login page tells the user what happened
        Document page = Jsoup.parse(given().get("/login?notice=password-set").asString());
        assertTrue(page.selectFirst("[role=status]").text().contains("Your password is set"));
    }

    @Test
    void theLinkCannotBeUsedASecondTime() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        recovery.sendTo(member.id());
        String token = accounts.recoveryToken(member.email());
        String cookie = openLink(token);
        assertEquals(303, choose(cookie, GOOD, GOOD).statusCode());

        assertEquals(
                400,
                choose(cookie, "Another-brand-new-password-2", "Another-brand-new-password-2")
                        .statusCode());
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("X-Forwarded-For", TestAccounts.randomAddress())
                .queryParam("token", token)
                .get("/reset-password")
                .then()
                .statusCode(400);
        assertTrue(
                login.login(member.email(), GOOD) instanceof LoginResult.LoggedIn,
                "the first password stays");
    }

    @Test
    void anOlderLinkStopsWorkingWhenANewOneIsSent() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        recovery.sendTo(member.id());
        String first = accounts.recoveryToken(member.email());
        recovery.sendTo(member.id());
        String second = accounts.recoveryToken(member.email());
        assertNotEquals(first, second);
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("X-Forwarded-For", TestAccounts.randomAddress())
                .queryParam("token", first)
                .get("/reset-password")
                .then()
                .statusCode(400);
        assertEquals(
                303,
                given().redirects()
                        .follow(false)
                        .header("X-Forwarded-For", TestAccounts.randomAddress())
                        .queryParam("token", second)
                        .get("/reset-password")
                        .statusCode());
    }

    private static void assertNotEquals(String a, String b) {
        assertFalse(a.equals(b));
    }

    @Test
    void choosingAPasswordFromAnotherSiteIsRefused() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        recovery.sendTo(member.id());
        String cookie = openLink(accounts.recoveryToken(member.email()));
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Origin", "https://evil.example")
                .header("Cookie", cookie)
                .formParam("password", GOOD)
                .formParam("password2", GOOD)
                .post("/set-password")
                .then()
                .statusCode(403);
        assertTrue(
                login.login(member.email(), TestAccounts.PASSWORD) instanceof LoginResult.LoggedIn,
                "nothing changed");
    }

    @Test
    void manyWrongLinksFromOneVisitorGetA429() {
        String visitor = TestAccounts.randomAddress();
        for (int i = 0; i < 20; i++) {
            given().redirects()
                    .follow(false)
                    .header("X-Forwarded-For", visitor)
                    .queryParam("token", UUID.randomUUID().toString().replace("-", ""))
                    .get("/reset-password")
                    .then()
                    .statusCode(400);
        }
        Response refused =
                given().redirects()
                        .follow(false)
                        .accept("text/html")
                        .header("X-Forwarded-For", visitor)
                        .queryParam("token", "x")
                        .get("/reset-password");
        assertEquals(429, refused.statusCode());
        assertNotNull(refused.header("Retry-After"));
    }

    // Nothing secret anywhere it should not be

    @Test
    void theTokenAndThePasswordAreNeverInTheLogsOrInAnyResponseBody() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        String password = "Recognizable-web-password-Kp3";
        List<String> collected = new java.util.ArrayList<>();
        List<LogRecord> logs =
                TestLogs.capture(() -> {
                    ask(member.email());
                    String token = accounts.recoveryToken(member.email());
                    collected.add(token);
                    Response open =
                            given().redirects()
                                    .follow(false)
                                    .header("X-Forwarded-For", TestAccounts.randomAddress())
                                    .queryParam("token", token)
                                    .get("/reset-password");
                    String cookie = open.header("Set-Cookie").split(";")[0];
                    collected.add(given().header("Cookie", cookie).get("/set-password").asString());
                    collected.add(choose(cookie, "short", "short").asString());
                    collected.add(choose(cookie, password, password).asString());
                });
        String token = collected.get(0);
        for (LogRecord record : logs) {
            String text = TestLogs.everything(record);
            assertFalse(text.contains(token));
            assertFalse(text.contains(password));
            assertFalse(text.contains(member.email()));
        }
        for (String body : collected.subList(1, collected.size())) {
            assertFalse(body.contains(token), "no page repeats the token");
            assertFalse(body.contains(password), "no page repeats the password");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/forgot-password", "/set-password", "/reset-password?token=x"})
    void everyRecoveryPageHasTheStrictHeaders(String path) {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("X-Forwarded-For", TestAccounts.randomAddress())
                .get(path)
                .then()
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        "Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self'"));
    }
}
