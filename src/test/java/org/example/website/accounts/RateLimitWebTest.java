package org.example.website.accounts;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.time.Duration;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.TestClock;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** LOG-13 through the login page: the 429 answer and who it applies to. */
@QuarkusTest
class RateLimitWebTest {

    @Inject TestAccounts accounts;
    @Inject TestClock clock;

    @AfterEach
    void resetTheClock() {
        clock.reset();
    }

    private static Response post(String email, String password, String address) {
        return given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Origin", TestAccounts.ORIGIN)
                .header("X-Forwarded-For", address)
                .formParam("email", email)
                .formParam("password", password)
                .post("/login");
    }

    @Test
    void aSecondAttemptDuringTheWaitGetsA429WithTheMessageAndTheWait() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String address = TestAccounts.randomAddress();

        assertEquals(200, post(user.email(), "wrong-password-xyz", address).statusCode());
        Response refused = post(user.email(), TestAccounts.PASSWORD, address); // even the right one

        assertEquals(429, refused.statusCode());
        assertEquals("1", refused.header("Retry-After"));
        assertEquals("no-store", refused.header("Cache-Control"));
        assertFalse(
                refused.headers().hasHeaderWithName("Set-Cookie"), "no session during the wait");
        Document page = Jsoup.parse(refused.asString());
        assertTrue(
                page.selectFirst("[role=alert]")
                        .text()
                        .contains("Too many attempts, try again in a moment"));
        assertEquals(
                user.email(), page.selectFirst("input[name=email]").val(), "the address stays");
        assertEquals("", page.selectFirst("input[name=password]").val());

        clock.advance(Duration.ofSeconds(2));
        assertEquals(303, post(user.email(), TestAccounts.PASSWORD, address).statusCode());
    }

    @Test
    void theWaitIsTheSameForAnAddressThatHasNoAccount() {
        String address = TestAccounts.randomAddress();
        String unknown = "nobody-" + System.nanoTime() + "@example.test";
        post(unknown, "wrong-password-xyz", address);
        Response refused = post(unknown, "wrong-password-xyz", address);
        assertEquals(429, refused.statusCode());
        assertEquals("1", refused.header("Retry-After"));
        assertTrue(refused.asString().contains("Too many attempts, try again in a moment"));
    }

    @Test
    void theRefusedPageHasTheSameShapeWhoeverIsAsked() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        String unknown = "nobody-" + System.nanoTime() + "@example.test";
        String a = TestAccounts.randomAddress();
        String b = TestAccounts.randomAddress();
        post(user.email(), "wrong-password-xyz", a);
        post(unknown, "wrong-password-xyz", b);
        String known =
                post(user.email(), "wrong-password-xyz", a)
                        .asString()
                        .replace(user.email(), "ADDRESS");
        String missing =
                post(unknown, "wrong-password-xyz", b).asString().replace(unknown, "ADDRESS");
        assertEquals(known, missing, "the 429 page does not show whether the account exists");
    }

    @Test
    void anotherVisitorCanStillLogIn() {
        UserSummary user = accounts.newUser(Role.MEMBER);
        post(user.email(), "wrong-password-xyz", TestAccounts.randomAddress());
        post(user.email(), "wrong-password-xyz", "203.0.113.50");
        assertEquals(429, post(user.email(), TestAccounts.PASSWORD, "203.0.113.50").statusCode());
        assertEquals(
                303,
                post(user.email(), TestAccounts.PASSWORD, TestAccounts.randomAddress())
                        .statusCode());
    }

    @Test
    void aForgedForwardedHeaderCannotEscapeTheWait() {
        // The proxy adds the real address at the end; a visitor can only add entries in front of it
        UserSummary user = accounts.newUser(Role.MEMBER);
        String real = TestAccounts.randomAddress();
        post(user.email(), "wrong-password-xyz", "198.51.100.1, " + real);
        Response retry = post(user.email(), TestAccounts.PASSWORD, "203.0.113.77, " + real);
        assertEquals(
                429,
                retry.statusCode(),
                "a different fake entry in front does not make a new visitor");
        assertNotNull(retry.header("Retry-After"));
    }

    @Test
    void aBusyHashingPoolIsNotReportedAsAWrongPassword() {
        // The page for "busy" is the 503 page, never the login error (LOG-23)
        given().accept("text/html").get("/test-only/errors/unavailable").then().statusCode(503);
    }
}
