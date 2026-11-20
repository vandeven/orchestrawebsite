package org.example.website.accounts.internal.login;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.login.LoginResult;
import org.example.website.accounts.api.login.LoginService;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserService;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.accounts.internal.security.SessionTokens;
import org.example.website.accounts.internal.store.SessionRepository;
import org.example.website.accounts.internal.store.UserRepository;
import org.example.website.platform.TestClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * LOG-9 to LOG-11: sessions are stored as hashes, expire after a day and follow the user record.
 */
@QuarkusTest
class SessionTest {

    @Inject TestAccounts accounts;
    @Inject LoginService loginService;
    @Inject UserService userService;
    @Inject UserRepository users;
    @Inject SessionRepository sessions;
    @Inject TestClock clock;

    @AfterEach
    void resetTheClock() {
        clock.reset();
    }

    private static int statusOf(String cookie, String path) {
        return given().redirects().follow(false).header("Cookie", cookie).get(path).statusCode();
    }

    private static String memberPageStatus(String token) {
        return statusOf("__Host-session=" + token, "/members") + "";
    }

    // The cookie (LOG-11)

    @Test
    void theSessionCookieHasTheRightAttributesAndNoUserData() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        Response response =
                given().redirects()
                        .follow(false)
                        .header("Origin", TestAccounts.ORIGIN)
                        .formParam("email", user.email())
                        .formParam("password", TestAccounts.PASSWORD)
                        .post("/login");
        List<String> cookies = response.headers().getValues("Set-Cookie");
        assertEquals(1, cookies.size(), "the one cookie of the website");
        String cookie = cookies.get(0);

        assertTrue(cookie.startsWith("__Host-session="), cookie);
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("Secure"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
        assertTrue(cookie.contains("Path=/"), cookie);
        assertTrue(cookie.contains("Max-Age=86400"), cookie); // 24 hours
        assertFalse(cookie.contains("Domain"), "the __Host- prefix forbids a Domain");

        Matcher value = Pattern.compile("__Host-session=([^;]+)").matcher(cookie);
        assertTrue(value.find());
        String token = value.group(1);
        assertEquals(43, token.length(), "256 bits as base64url");
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertFalse(token.contains(user.id().toString()));
        assertFalse(cookie.toLowerCase().contains("example.test"));
        assertFalse(cookie.contains(TestAccounts.PASSWORD));
    }

    @Test
    void everyLoginGetsADifferentToken() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String first =
                ((LoginResult.LoggedIn) loginService.login(user.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        String second =
                ((LoginResult.LoggedIn) loginService.login(user.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        assertNotEquals(first, second);
    }

    @Test
    void onlyAHashOfTheTokenIsStored() {
        // LOG-11: a copy of the database must not be enough to use a session
        String token = accounts.sessionToken(Role.MEMBER);
        assertTrue(sessions.findByTokenHash(token).isEmpty(), "the token itself is not a key");
        assertTrue(sessions.findByTokenHash(SessionTokens.hash(token)).isPresent());
        assertNotEquals(token, SessionTokens.hash(token));
        assertEquals(64, SessionTokens.hash(token).length(), "a SHA-256 hash in hex");
    }

    // Expiry (LOG-10)

    @Test
    void aSessionLastsExactlyOneDayWhateverTheActivity() {
        String cookie = "__Host-session=" + accounts.sessionToken(Role.MEMBER);
        assertEquals(200, statusOf(cookie, "/members"));

        clock.advance(Duration.ofHours(23).plusMinutes(59));
        assertEquals(200, statusOf(cookie, "/members"), "still valid just before 24 hours");
        // Using the website did not extend the session
        clock.advance(Duration.ofMinutes(2));
        Response expired =
                given().redirects().follow(false).header("Cookie", cookie).get("/members");
        expired.then().statusCode(303);
        assertEquals(
                "/login?return=%2Fmembers",
                expired.header("Location"), "sent back to the login page (LOG-10)");
    }

    @Test
    void anExpiredSessionIsRemovedFromTheStore() {
        String token = accounts.sessionToken(Role.MEMBER);
        clock.advance(Duration.ofHours(25));
        assertTrue(loginService.userForSession(token).isEmpty());
        assertTrue(sessions.findByTokenHash(SessionTokens.hash(token)).isEmpty());
    }

    // Tokens that must never work

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "x",
                "garbage",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "../../etc/passwd",
                "' OR '1'='1",
                "<script>alert(1)</script>",
                "0000000000000000000000000000000000000000000000000000000000000000"
            })
    void invalidTokensAreRefused(String token) {
        assertEquals("303", memberPageStatus(token));
    }

    @Test
    void aTamperedOrShortenedTokenIsRefused() {
        String token = accounts.sessionToken(Role.MEMBER);
        assertEquals("200", memberPageStatus(token));
        assertEquals("303", memberPageStatus(token.substring(0, token.length() - 1)));
        assertEquals("303", memberPageStatus(token + "A"));
        char last = token.charAt(token.length() - 1);
        String changed = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertEquals("303", memberPageStatus(changed));
        assertEquals(
                "303",
                memberPageStatus(
                        token.toLowerCase().equals(token)
                                ? token.toUpperCase()
                                : token.toLowerCase()));
    }

    // No session fixation (LOG-11)

    @Test
    void aTokenThatExistedBeforeTheLoginIsNotValidAfterwards() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        // A token that was handed out or guessed before the login
        String planted = SessionTokens.newToken();
        assertEquals("303", memberPageStatus(planted));

        Response login =
                given().redirects()
                        .follow(false)
                        .header("Origin", TestAccounts.ORIGIN)
                        .header("Cookie", "__Host-session=" + planted)
                        .formParam("email", user.email())
                        .formParam("password", TestAccounts.PASSWORD)
                        .post("/login");
        login.then().statusCode(303);
        Matcher value =
                Pattern.compile("__Host-session=([^;]+)").matcher(login.header("Set-Cookie"));
        assertTrue(value.find());
        assertNotEquals(planted, value.group(1));
        assertEquals("303", memberPageStatus(planted), "the planted token still does nothing");
        assertEquals("200", memberPageStatus(value.group(1)));
    }

    @Test
    void anOldSessionEndsWhenTheSameBrowserLogsInAgain() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String first =
                ((LoginResult.LoggedIn) loginService.login(user.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        assertEquals("200", memberPageStatus(first));
        given().redirects()
                .follow(false)
                .header("Origin", TestAccounts.ORIGIN)
                .header("Cookie", "__Host-session=" + first)
                .formParam("email", user.email())
                .formParam("password", TestAccounts.PASSWORD)
                .post("/login")
                .then()
                .statusCode(303);
        assertEquals("303", memberPageStatus(first));
    }

    // The user record counts on every request (LOG-11, USR-9, USR-10, USR-21)

    @Test
    void aRemovedUserLosesTheSessionAtOnce() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String token =
                ((LoginResult.LoggedIn) loginService.login(user.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        assertEquals("200", memberPageStatus(token));
        users.delete(user.id());
        assertEquals("303", memberPageStatus(token));
    }

    @Test
    void aChangedRoleCountsOnTheNextRequest() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String token =
                ((LoginResult.LoggedIn) loginService.login(user.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        String cookie = "__Host-session=" + token;
        String before = given().header("Cookie", cookie).get("/").asString();
        assertFalse(before.contains("User management"));

        users.update(users.findById(user.id()).orElseThrow().withRole(Role.ADMIN));
        String after = given().header("Cookie", cookie).get("/").asString();
        assertTrue(
                after.contains("User management"), "the new role counts without logging in again");

        users.update(users.findById(user.id()).orElseThrow().withRole(Role.MEMBER));
        assertFalse(
                given().header("Cookie", cookie).get("/").asString().contains("User management"));
    }

    @Test
    void settingANewPasswordEndsAllSessionsOfTheUser() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String one =
                ((LoginResult.LoggedIn) loginService.login(user.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        String two =
                ((LoginResult.LoggedIn) loginService.login(user.email(), TestAccounts.PASSWORD))
                        .sessionToken();
        String other = accounts.sessionToken(Role.MEMBER);

        userService.setPassword(user.id(), "A-brand-new-password-1");
        assertEquals("303", memberPageStatus(one));
        assertEquals("303", memberPageStatus(two));
        assertEquals("200", memberPageStatus(other), "sessions of other users are not touched");
        assertTrue(
                loginService.login(user.email(), TestAccounts.PASSWORD)
                        instanceof LoginResult.Failed);
        assertTrue(
                loginService.login(user.email(), "A-brand-new-password-1")
                        instanceof LoginResult.LoggedIn);
    }

    @Test
    void twoSessionsNeverSeeEachOthersAccount() {
        String member = accounts.cookie(Role.MEMBER);
        String admin = accounts.cookie(Role.ADMIN);
        String memberMenu = given().header("Cookie", member).get("/").asString();
        String adminMenu = given().header("Cookie", admin).get("/").asString();
        assertFalse(memberMenu.contains("User management"));
        assertTrue(adminMenu.contains("User management"));
    }
}
