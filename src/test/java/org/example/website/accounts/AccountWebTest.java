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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogRecord;
import org.example.website.TestAccounts;
import org.example.website.TestLogs;
import org.example.website.accounts.api.login.LoginResult;
import org.example.website.accounts.api.login.LoginService;
import org.example.website.accounts.api.login.SessionCookie;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.TestClock;
import org.example.website.platform.api.mail.Outbox;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The My account page: what it shows and changing your password through it (ACC-1 to ACC-9). */
@QuarkusTest
class AccountWebTest {

    private static final String NEW = "A-brand-new-password-for-the-page";

    @Inject TestAccounts accounts;
    @Inject LoginService login;
    @Inject Outbox outbox;
    @Inject TestClock clock;

    @BeforeEach
    void clean() {
        outbox.clear();
    }

    @AfterEach
    void reset() {
        clock.reset();
    }

    private String sessionOf(UserSummary user) {
        return ((LoginResult.LoggedIn) login.login(user.email(), TestAccounts.PASSWORD))
                .sessionToken();
    }

    private static String cookie(String token) {
        return SessionCookie.NAME + "=" + token;
    }

    private static RequestSpecification as(String token) {
        return given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Cookie", cookie(token))
                .header("Origin", TestAccounts.ORIGIN)
                .header("X-Forwarded-For", TestAccounts.randomAddress())
                .contentType("application/x-www-form-urlencoded");
    }

    private static Response change(String token, String current, String password, String again) {
        return as(token)
                .formParam("current", current)
                .formParam("password", password)
                .formParam("password2", again)
                .post("/account");
    }

    // Access (ACC-1) and what it shows (ACC-2)

    @Test
    void visitorsAreSentToTheLoginPageAndEveryRoleCanOpenIt() {
        given().redirects()
                .follow(false)
                .get("/account")
                .then()
                .statusCode(303)
                .header("Location", "/login?return=%2Faccount");
        for (Role role : Role.values()) {
            as(accounts.sessionToken(role))
                    .get("/account")
                    .then()
                    .statusCode(200)
                    .header("Cache-Control", "private, no-store");
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void thePageShowsTheEmailAddressAndRole(Role role) {
        String email =
                role == Role.MASTER ? TestAccounts.MASTER_EMAIL : accounts.newUser(role).email();
        String token =
                role == Role.MASTER
                        ? accounts.sessionToken(Role.MASTER)
                        : sessionOf(
                                login.userForSession(accounts.sessionToken(role)).orElseThrow());
        if (role != Role.MASTER) {
            UserSummary user = accounts.newUser(role);
            email = user.email();
            token = sessionOf(user);
        }
        Document page = Jsoup.parse(as(token).get("/account").asString());
        assertEquals("My account", page.selectFirst("h1").text());
        assertEquals(email, page.selectFirst("dd").text());
        assertEquals(
                role.name().charAt(0) + role.name().substring(1).toLowerCase(),
                page.select("dd").get(1).text());
        // Read only: there is no field to change them
        assertTrue(page.select("input[name=email], input[name=role]").isEmpty());
    }

    @Test
    void theMenuLinksToThePageAndMarksIt() {
        Document page =
                Jsoup.parse(as(accounts.sessionToken(Role.MEMBER)).get("/account").asString());
        assertEquals(
                "My account", page.selectFirst("a[href='/account'][aria-current=page]").text());
    }

    @Test
    void theFormHasTheRightFields() {
        Document page =
                Jsoup.parse(as(accounts.sessionToken(Role.MEMBER)).get("/account").asString());
        assertEquals(
                "current-password", page.selectFirst("input[name=current]").attr("autocomplete"));
        for (String name : List.of("password", "password2")) {
            var field = page.selectFirst("input[name=" + name + "]");
            assertEquals("new-password", field.attr("autocomplete"));
            assertEquals("12", field.attr("minlength"));
            assertEquals("128", field.attr("maxlength"));
            assertEquals("password", field.attr("type"));
            assertNotNull(page.selectFirst("label[for=" + field.id() + "]"));
        }
        assertNotNull(page.selectFirst("label[for=current]"));
        assertTrue(
                page.selectFirst("[data-toggle-password]").hasAttr("hidden"),
                "show and hide only with JavaScript");
        assertEquals("/js/password.js?v=dev", page.select("script[src]").last().attr("src"));
        assertEquals("password-hint", page.selectFirst("#password").attr("aria-describedby"));
    }

    @Test
    void theMasterSeesNoFormAndADirectRequestIsRefused() {
        String master = accounts.sessionToken(Role.MASTER);
        Document page = Jsoup.parse(as(master).get("/account").asString());
        assertTrue(page.select("form[action='/account'], input[type=password]").isEmpty(), "ACC-8");
        assertTrue(page.selectFirst("main").text().contains("set by the owner"));
        assertEquals(403, change(master, TestAccounts.MASTER_PASSWORD, NEW, NEW).statusCode());
        assertTrue(
                login.login(TestAccounts.MASTER_EMAIL, TestAccounts.MASTER_PASSWORD)
                        instanceof LoginResult.LoggedIn);
    }

    // Changing the password (ACC-3 to ACC-6)

    @Test
    void aCorrectChangeStoresTheNewPasswordEndsOtherSessionsAndKeepsThisDeviceLoggedIn() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String thisDevice = sessionOf(user);
        String otherDevice = sessionOf(user);

        Response response = change(thisDevice, TestAccounts.PASSWORD, NEW, NEW);
        response.then()
                .statusCode(303)
                .header("Location", "/account?notice=password-changed")
                .header("Cache-Control", "private, no-store");

        String setCookie = response.header("Set-Cookie");
        assertTrue(setCookie.startsWith("__Host-session="), setCookie);
        assertTrue(
                setCookie.contains("HttpOnly")
                        && setCookie.contains("Secure")
                        && setCookie.contains("SameSite=Lax"));
        String newToken = setCookie.split(";")[0].substring("__Host-session=".length());
        assertFalse(newToken.equals(thisDevice));

        as(newToken).get("/account?notice=password-changed").then().statusCode(200);
        assertEquals(
                303,
                as(thisDevice).get("/account").statusCode(),
                "the old token of this device is dead");
        assertEquals(
                303, as(otherDevice).get("/account").statusCode(), "other devices are logged out");
        assertTrue(login.login(user.email(), NEW) instanceof LoginResult.LoggedIn);
        assertTrue(login.login(user.email(), TestAccounts.PASSWORD) instanceof LoginResult.Failed);
    }

    @Test
    void theConfirmationIsShownAndAnEmailIsSent() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String token = sessionOf(user);
        Response response = change(token, TestAccounts.PASSWORD, NEW, NEW);
        String newToken =
                response.header("Set-Cookie").split(";")[0].substring("__Host-session=".length());
        Document page =
                Jsoup.parse(as(newToken).get("/account?notice=password-changed").asString());
        assertTrue(page.selectFirst("[role=status]").text().contains("Your password was changed"));
        assertEquals(1, accounts.emailsTo(user.email()));
        assertTrue(
                Jsoup.parse(as(newToken).get("/account").asString())
                        .select("[role=status]")
                        .isEmpty(),
                "no notice without the key");
        assertTrue(
                Jsoup.parse(as(newToken).get("/account?notice=<script>").asString())
                        .select("[role=status]")
                        .isEmpty());
    }

    // A wrong current password (ACC-4)

    @Test
    void aWrongCurrentPasswordShowsOneGeneralErrorAndChangesNothing() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String token = sessionOf(user);
        Response response = change(token, "not-my-password-1", NEW, NEW);
        assertEquals(400, response.statusCode());
        Document page = Jsoup.parse(response.asString());
        assertEquals(
                "The current password is not correct", page.selectFirst("#current-error").text());
        assertEquals("true", page.selectFirst("#current").attr("aria-invalid"));
        assertEquals("", page.selectFirst("#current").val(), "passwords are not shown again");
        assertEquals("", page.selectFirst("#password").val());
        assertTrue(
                login.login(user.email(), TestAccounts.PASSWORD) instanceof LoginResult.LoggedIn);
        assertEquals(0, accounts.emailsTo(user.email()));
    }

    @Test
    void manyAttemptsFromOneVisitorAreSlowedDownWithA429() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String token = sessionOf(user);
        String address = TestAccounts.randomAddress();
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Cookie", cookie(token))
                .header("Origin", TestAccounts.ORIGIN)
                .header("X-Forwarded-For", address)
                .formParam("current", "wrong-one-xyz")
                .formParam("password", NEW)
                .formParam("password2", NEW)
                .post("/account")
                .then()
                .statusCode(400);
        Response refused =
                given().redirects()
                        .follow(false)
                        .accept("text/html")
                        .header("Cookie", cookie(token))
                        .header("Origin", TestAccounts.ORIGIN)
                        .header("X-Forwarded-For", address)
                        .formParam("current", TestAccounts.PASSWORD)
                        .formParam("password", NEW)
                        .formParam("password2", NEW)
                        .post("/account");
        assertEquals(429, refused.statusCode());
        assertNotNull(refused.header("Retry-After"));
        assertTrue(
                login.login(user.email(), TestAccounts.PASSWORD) instanceof LoginResult.LoggedIn,
                "nothing changed");
    }

    // The rules (ACC-5)

    @Test
    void everyRuleHasAMessageNextToTheField() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String token = sessionOf(user);
        String p = TestAccounts.PASSWORD;

        assertEquals(
                "Enter your current password",
                error(change(token, "", NEW, NEW), "#current-error"));
        assertEquals(
                "Use at least 12 characters",
                error(change(token, p, "aB3$xY9!qW7", "aB3$xY9!qW7"), "#password-error")); // 11
        assertEquals(
                "Use at most 128 characters",
                error(
                        change(token, p, ("aB3$xY9!qW7z".repeat(11)).substring(0, 129), "x"),
                        "#password-error")); // 129
        assertEquals(
                "That password is too common, choose another one",
                error(change(token, p, "password1234", "password1234"), "#password-error"));
        assertEquals(
                "The passwords are not the same",
                error(change(token, p, NEW, NEW + "x"), "#password2-error"));
        assertEquals(
                "The new password must be different from the current one",
                error(change(token, p, p, p), "#password-error"));
        assertEquals("Enter a new password", error(change(token, p, "", ""), "#password-error"));

        assertTrue(login.login(user.email(), p) instanceof LoginResult.LoggedIn, "nothing changed");
        assertEquals(
                303,
                change(token, p, "aB3$xY9!qW7z", "aB3$xY9!qW7z").statusCode(),
                "12 characters is fine");
    }

    private static String error(Response response, String selector) {
        assertEquals(400, response.statusCode());
        Document page = Jsoup.parse(response.asString());
        assertEquals("true", page.selectFirst(selector.replace("-error", "")).attr("aria-invalid"));
        return page.selectFirst(selector).text();
    }

    @Test
    void the128CharacterBoundaryWorks() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String long128 = ("aB3$xY9!qW7z".repeat(11)).substring(0, 128);
        assertEquals(
                303, change(sessionOf(user), TestAccounts.PASSWORD, long128, long128).statusCode());
        assertTrue(login.login(user.email(), long128) instanceof LoginResult.LoggedIn);
    }

    // Security (ACC-9)

    @Test
    void aRequestFromAnotherSiteIsRefusedAndChangesNothing() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String token = sessionOf(user);
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Cookie", cookie(token))
                .header("Origin", "https://evil.example")
                .formParam("current", TestAccounts.PASSWORD)
                .formParam("password", NEW)
                .formParam("password2", NEW)
                .post("/account")
                .then()
                .statusCode(403);
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Cookie", cookie(token))
                .formParam("current", TestAccounts.PASSWORD)
                .formParam("password", NEW)
                .formParam("password2", NEW)
                .post("/account")
                .then()
                .statusCode(403);
        assertTrue(
                login.login(user.email(), TestAccounts.PASSWORD) instanceof LoginResult.LoggedIn);
    }

    @Test
    void aVisitorCannotChangeAPassword() {
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Origin", TestAccounts.ORIGIN)
                .formParam("current", "x")
                .formParam("password", NEW)
                .formParam("password2", NEW)
                .post("/account")
                .then()
                .statusCode(403);
    }

    @Test
    void thePasswordsAreNeverInTheLogsOrInAnyPage() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String token = sessionOf(user);
        String typo = "Recognizable-typo-password-Qw9";
        String fresh = "Recognizable-fresh-password-Lk2";
        List<String> pages = new ArrayList<>();
        List<LogRecord> logs =
                TestLogs.capture(() -> {
                    pages.add(change(token, typo, fresh, fresh).asString());
                    pages.add(change(token, TestAccounts.PASSWORD, fresh, fresh + "x").asString());
                    clock.advance(Duration.ofSeconds(5));
                    pages.add(change(token, TestAccounts.PASSWORD, fresh, fresh).asString());
                });
        for (LogRecord record : logs) {
            String text = TestLogs.everything(record);
            for (String secret : List.of(typo, fresh, TestAccounts.PASSWORD)) {
                assertFalse(text.contains(secret));
            }
        }
        for (String page : pages) {
            for (String secret : List.of(typo, fresh, TestAccounts.PASSWORD)) {
                assertFalse(page.contains(secret), "no page repeats a password");
            }
        }
    }

    @Test
    void thePageSendsTheStrictHeadersAndLoadsNothingFromOtherSites() {
        Response response = as(accounts.sessionToken(Role.MEMBER)).get("/account");
        response.then()
                .header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        "Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self'"));
        Document page = Jsoup.parse(response.asString());
        assertTrue(
                page.select("[src], [href], [action]").stream()
                        .noneMatch(e ->
                                e.attr("src").startsWith("http")
                                        || e.attr("href").startsWith("http")));
        assertTrue(page.select("[style], style, script:not([src])").isEmpty());
    }
}
