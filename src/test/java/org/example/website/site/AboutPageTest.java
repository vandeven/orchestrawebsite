package org.example.website.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The About page, the home page of the website (ABO-1 to ABO-8). */
@QuarkusTest
class AboutPageTest {

    @Inject EditableTexts texts;
    @Inject TestAccounts accounts;

    @AfterEach
    void clearTheText() {
        texts.save(TextKey.ABOUT, "", accounts.newUser(Role.ADMIN));
    }

    private void write(String html) {
        UserSummary admin = accounts.newUser(Role.ADMIN);
        texts.save(TextKey.ABOUT, html, admin);
    }

    private Response home() {
        return given().accept("text/html").get("/");
    }

    @Test
    void theHomePageIsPublicAndHasTheFixedTitle() {
        Response response = home();
        assertEquals(200, response.statusCode());
        Document page = Jsoup.parse(response.asString());
        assertEquals("About", page.selectFirst("h1").text());
    }

    @Test
    void anEmptyTextShowsThePlaceholder() {
        Document page = Jsoup.parse(home().asString());
        assertTrue(page.text().contains("has not been written yet"));
        assertTrue(page.select(".page-text").isEmpty());
    }

    @Test
    void theSavedTextIsShownWithItsFormatting() {
        write("<h2>The band</h2><p>We <strong>play</strong> every week.</p>");
        Document page = Jsoup.parse(home().asString());
        assertEquals("The band", page.selectFirst(".page-text h2").text());
        assertEquals("play", page.selectFirst(".page-text strong").text());
        assertFalse(page.text().contains("has not been written yet"));
    }

    @Test
    void harmfulHtmlNeverReachesTheVisitor() {
        write("<p>Hi</p><script>alert(1)</script><img src=x onerror=alert(1)>");
        String body = home().asString();
        assertFalse(body.contains("alert(1)"), body);
        assertFalse(body.contains("onerror"));
    }

    @Test
    void theMenuLinksToTheHomePageAndThePageHasNoLoginForm() {
        Document page = Jsoup.parse(home().asString());
        var about = page.selectFirst("nav a.site-nav-link[href='/']");
        assertNotNull(about);
        assertEquals("About", about.text());
        assertEquals("page", about.attr("aria-current"));
        assertTrue(page.select("main form").isEmpty(), "no login form on the page");
        assertTrue(page.select("main a[href='/forgot-password']").isEmpty());
    }

    @Test
    void anonymousVisitorsGetAShortCacheAndLoggedInVisitorsNone() {
        Response anonymous = home();
        assertEquals("public, max-age=60", anonymous.header("Cache-Control"));
        assertTrue(anonymous.header("Vary").contains("Cookie"));
        Response member =
                given().accept("text/html").header("Cookie", accounts.cookie(Role.MEMBER)).get("/");
        assertEquals("private, no-store", member.header("Cache-Control"));
    }

    @Test
    void theOldAddressMovesForGoodToTheHomePage() {
        Response response = RestAssured.given().redirects().follow(false).get("/about");
        assertEquals(301, response.statusCode());
        assertEquals("/", URI_PATH.apply(response.header("Location")));
    }

    @Test
    void theCanonicalAddressIsTheHomePage() {
        Document page = Jsoup.parse(home().asString());
        var canonical = page.selectFirst("link[rel=canonical]");
        assertNotNull(canonical);
        assertTrue(canonical.attr("href").endsWith("/"));
        assertFalse(canonical.attr("href").endsWith("/about"));
    }

    private static final java.util.function.Function<String, String> URI_PATH =
            location -> java.net.URI.create(location).getPath();
}
