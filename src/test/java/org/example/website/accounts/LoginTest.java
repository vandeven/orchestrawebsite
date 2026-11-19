package org.example.website.accounts;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserService;
import org.example.website.accounts.api.UserSummary;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** LOG-1 to LOG-9: the login page, logging in and out, and the same answer for every failure. */
@QuarkusTest
class LoginTest {

    @Inject TestAccounts accounts;
    @Inject UserService users;

    static RequestSpecification form() {
        return given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Origin", TestAccounts.ORIGIN)
                .header("X-Forwarded-For", TestAccounts.randomAddress())
                .contentType("application/x-www-form-urlencoded");
    }

    static Response login(String email, String password) {
        return form().formParam("email", email).formParam("password", password).post("/login");
    }

    // The page (LOG-1, LOG-2, LOG-8)

    @Test
    void theLoginPageHasTheFormAndTheRecoveryLink() {
        Response response = given().accept("text/html").get("/login");
        response.then().statusCode(200).header("Cache-Control", "no-store");
        Document page = Jsoup.parse(response.asString());
        assertEquals("Log in", page.selectFirst("h1").text());
        var email = page.selectFirst("input[name=email]");
        assertEquals("email", email.attr("type"));
        assertEquals("username", email.attr("autocomplete"));
        assertEquals("254", email.attr("maxlength"));
        assertTrue(email.hasAttr("required"));
        var password = page.selectFirst("input[name=password]");
        assertEquals("password", password.attr("type"));
        assertEquals("current-password", password.attr("autocomplete"));
        assertEquals("128", password.attr("maxlength"));
        assertEquals("Log in", page.selectFirst("form button[type=submit]").text());
        assertEquals(
                "/forgot-password", page.selectFirst("a:contains(Forgot password?)").attr("href"));
        assertNotNull(page.selectFirst("label[for=email]"));
        assertNotNull(page.selectFirst("label[for=password]"));
    }

    @Test
    void theShowPasswordButtonIsHiddenUntilTheScriptRuns() {
        Document page = Jsoup.parse(given().get("/login").asString());
        var toggle = page.selectFirst("[data-toggle-password]");
        assertTrue(toggle.hasAttr("hidden"), "without JavaScript nobody sees a dead button");
        assertEquals("/js/password.js", page.select("script[src]").last().attr("src"));
        given().get("/js/password.js").then().statusCode(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/does-not-exist"})
    void otherPagesHaveNoLoginFormOrPasswordField(String path) {
        // LOG-1: the login form is only on the login page
        Document page = Jsoup.parse(given().accept("text/html").get(path).asString());
        assertTrue(page.select("input[type=password]").isEmpty());
        assertTrue(page.select("form[action='/login']").isEmpty());
    }

    // Logging in (LOG-3, LOG-4)

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleCanLogInAndEndsOnTheMemberPage(Role role) {
        String email;
        String password;
        if (role == Role.MASTER) {
            email = TestAccounts.MASTER_EMAIL;
            password = TestAccounts.MASTER_PASSWORD;
        } else {
            email = accounts.newUser(role).email();
            password = TestAccounts.PASSWORD;
        }
        Response response = login(email, password);
        response.then().statusCode(303).header("Location", "/members");
        String cookie = response.header("Set-Cookie").split(";")[0];

        // The end-to-end flow of the spec: the member page opens with the new session
        given().accept("text/html")
                .header("Cookie", cookie)
                .get("/members")
                .then()
                .statusCode(200)
                .body(containsString("Member page"))
                .body(containsString("Log out"));
    }

    @Test
    void theAddressIsComparedWithoutRegardToCaseOrSurroundingSpaces() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        login("  " + user.email().toUpperCase() + " ", TestAccounts.PASSWORD)
                .then()
                .statusCode(303);
    }

    @Test
    void aVisitorWhoIsAlreadyLoggedInIsSentOnFromTheLoginPage() {
        // LOG-7
        given().redirects()
                .follow(false)
                .header("Cookie", accounts.cookie(Role.MEMBER))
                .get("/login")
                .then()
                .statusCode(303)
                .header("Location", "/members");
    }

    // A failed login (LOG-5)

    @Test
    void everyKindOfFailureGivesTheSameResponse() {
        UserSummary withPassword = accounts.newUser(Role.MEMBER);
        UserSummary withoutPassword =
                users.createUser("nopassword-" + System.nanoTime() + "@example.test", Role.MEMBER);
        String unknown = "nobody-" + System.nanoTime() + "@example.test";

        Response wrongPassword = login(withPassword.email(), "wrong password here");
        Response unknownAddress = login(unknown, "wrong password here");
        Response noPasswordSet = login(withoutPassword.email(), "wrong password here");
        // A user without a password cannot log in even with an empty-looking guess
        Response noPasswordLong = login(withoutPassword.email(), TestAccounts.PASSWORD);

        for (Response response :
                new Response[] {wrongPassword, unknownAddress, noPasswordSet, noPasswordLong}) {
            response.then().statusCode(200).header("Cache-Control", "no-store");
            assertFalse(
                    response.headers().hasHeaderWithName("Set-Cookie"), "no session is started");
            assertTrue(response.asString().contains("Email address or password is incorrect"));
        }
        // Apart from the address that was typed, the pages are identical
        String a = normalized(wrongPassword, withPassword.email());
        assertEquals(a, normalized(unknownAddress, unknown));
        assertEquals(a, normalized(noPasswordSet, withoutPassword.email()));
        assertEquals(a, normalized(noPasswordLong, withoutPassword.email()));
        assertEquals(wrongPassword.statusCode(), unknownAddress.statusCode());
    }

    private static String normalized(Response response, String email) {
        return response.asString().replace(email, "ADDRESS");
    }

    @Test
    void theAddressStaysInTheFormAndThePasswordIsEmptied() {
        Response response = login("someone@example.test", "typed-password-not-kept-1");
        Document page = Jsoup.parse(response.asString());
        assertEquals("someone@example.test", page.selectFirst("input[name=email]").val());
        assertEquals("", page.selectFirst("input[name=password]").val());
        assertFalse(response.asString().contains("typed-password-not-kept-1"), "LOG-21");
    }

    @Test
    void theErrorIsAnnouncedToScreenReaders() {
        Document page = Jsoup.parse(login("someone@example.test", "wrong").asString());
        var alert = page.selectFirst("[role=alert]");
        assertNotNull(alert);
        assertTrue(alert.text().contains("Email address or password is incorrect"));
    }

    // Format errors next to the field

    @Test
    void missingFieldsGetAMessageNextToTheField() {
        Response response = form().formParam("email", "").formParam("password", "").post("/login");
        response.then().statusCode(400);
        Document page = Jsoup.parse(response.asString());
        assertEquals("Enter your email address", page.selectFirst("#email-error").text());
        assertEquals("Enter your password", page.selectFirst("#password-error").text());
        assertEquals("true", page.selectFirst("#email").attr("aria-invalid"));
        assertEquals("email-error", page.selectFirst("#email").attr("aria-describedby"));
        assertEquals("password-error", page.selectFirst("#password").attr("aria-describedby"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not-an-address",
                "a@b",
                "a b@example.test",
                "@example.test",
                "a@@example.test"
            })
    void anInvalidAddressGetsAFormatMessage(String email) {
        Response response = login(email, "some password");
        response.then().statusCode(400);
        Document page = Jsoup.parse(response.asString());
        assertEquals("Enter a valid email address", page.selectFirst("#email-error").text());
        assertEquals(email.strip(), page.selectFirst("input[name=email]").val());
    }

    @Test
    void addressLengthBoundary() {
        String domain = "@example.test";
        String at254 = "a".repeat(254 - domain.length()) + domain;
        String at255 = "a".repeat(255 - domain.length()) + domain;
        assertEquals(254, at254.length());
        login(at254, "wrong password").then().statusCode(200); // valid format, wrong credentials
        login(at255, "wrong password").then().statusCode(400);
    }

    @Test
    void passwordLengthBoundary() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        login(user.email(), "x".repeat(128)).then().statusCode(200);
        // Too long to be right: the same general message, never an error page
        Response tooLong = login(user.email(), "x".repeat(129));
        tooLong.then()
                .statusCode(200)
                .body(containsString("Email address or password is incorrect"));
        // Far too long for the server to read at all: refused before it reaches the login code
        login(user.email(), "x".repeat(100_000)).then().statusCode(413);
    }

    // Return page (LOG-6)

    @Test
    void anonymousVisitorsAreSentToTheLoginPageAndReturnedAfterwards() {
        given().redirects()
                .follow(false)
                .get("/members")
                .then()
                .statusCode(303)
                .header("Location", "/login?return=%2Fmembers");

        UserSummary user = accounts.newUser(Role.MEMBER);
        Document page = Jsoup.parse(given().get("/login?return=/members").asString());
        assertEquals("/members", page.selectFirst("input[name=return]").val());

        form().formParam("email", user.email())
                .formParam("password", TestAccounts.PASSWORD)
                .formParam("return", "/members?from=login")
                .post("/login")
                .then()
                .statusCode(303)
                .header("Location", "/members?from=login");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://example.org",
                "//example.org",
                "///example.org",
                "/\\example.org",
                "\\\\example.org",
                "javascript:alert(1)",
                "/%2f%2fexample.org",
                "/%5cexample.org",
                "example.org",
                ""
            })
    void otherSitesAreNeverAReturnPage(String hostile) {
        UserSummary user = accounts.newUser(Role.MEMBER);
        form().formParam("email", user.email())
                .formParam("password", TestAccounts.PASSWORD)
                .formParam("return", hostile)
                .post("/login")
                .then()
                .statusCode(303)
                .header("Location", "/members");
        // The login page does not repeat a hostile value in the form either
        Document page = Jsoup.parse(given().queryParam("return", hostile).get("/login").asString());
        assertTrue(page.select("input[name=return]").isEmpty());
    }

    @Test
    void aReturnValueWithLineBreaksCannotSplitTheResponse() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        Response response =
                form().formParam("email", user.email())
                        .formParam("password", TestAccounts.PASSWORD)
                        .formParam("return", "/members\r\nSet-Cookie: evil=1")
                        .post("/login");
        response.then().statusCode(303).header("Location", "/members");
        assertEquals(1, response.headers().getValues("Set-Cookie").size());
    }

    // Logging out (LOG-9)

    @Test
    void loggingOutEndsTheSessionOnTheServer() {
        String cookie = accounts.cookie(Role.MEMBER);
        given().redirects()
                .follow(false)
                .header("Cookie", cookie)
                .get("/members")
                .then()
                .statusCode(200);

        Response response =
                given().redirects()
                        .follow(false)
                        .header("Origin", TestAccounts.ORIGIN)
                        .header("Cookie", cookie)
                        .post("/logout");
        response.then()
                .statusCode(303)
                .header("Location", "/")
                .header("Clear-Site-Data", "\"cookies\"");
        String setCookie = response.header("Set-Cookie");
        assertTrue(
                setCookie.startsWith("__Host-session=;") || setCookie.contains("Max-Age=0"),
                setCookie);

        // The old cookie, sent again, is no longer accepted
        given().redirects()
                .follow(false)
                .header("Cookie", cookie)
                .get("/members")
                .then()
                .statusCode(303)
                .header("Location", "/login?return=%2Fmembers");
    }

    @Test
    void loggingOutWithoutASessionDoesNoHarm() {
        given().redirects()
                .follow(false)
                .header("Origin", TestAccounts.ORIGIN)
                .post("/logout")
                .then()
                .statusCode(303)
                .header("Location", "/");
        given().redirects()
                .follow(false)
                .header("Origin", TestAccounts.ORIGIN)
                .header("Cookie", "__Host-session=garbage")
                .post("/logout")
                .then()
                .statusCode(303);
    }

    @Test
    void loggingOutIsNeverPossibleWithALink() {
        given().accept("text/html").get("/logout").then().statusCode(405);
    }

    @Test
    void theMenuShowsLogInOrLogOutBySession() {
        Document anonymous = Jsoup.parse(given().get("/").asString());
        assertFalse(anonymous.select(".site-nav-list").text().contains("Log out"));
        assertTrue(anonymous.select(".site-nav-list").text().contains("Log in"));

        Document member =
                Jsoup.parse(
                        given().header("Cookie", accounts.cookie(Role.MEMBER)).get("/").asString());
        assertTrue(member.select(".site-nav-list").text().contains("Log out"));
        assertFalse(member.select(".site-nav-list").text().contains("Log in"));
        given().header("Cookie", accounts.cookie(Role.MEMBER))
                .get("/")
                .then()
                .body(not(containsString("<a class=\"site-nav-link\" href=\"/login\"")));
        assertEquals(
                "private, no-store",
                given().header("Cookie", accounts.cookie(Role.MEMBER))
                        .get("/")
                        .header("Cache-Control"));
    }
}
