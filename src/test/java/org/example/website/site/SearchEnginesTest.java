package org.example.website.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.users.Role;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * robots.txt, the sitemap, security.txt, noindex, canonical addresses and sharing (plan, HRD-11).
 */
@QuarkusTest
class SearchEnginesTest {

    private static final String BASE = "http://localhost:8081";

    @Inject TestAccounts accounts;

    @Test
    void robotsBlocksTheSecureSectionAndPointsToTheSitemap() {
        Response response = given().get("/robots.txt");
        assertEquals(200, response.statusCode());
        assertTrue(response.contentType().startsWith("text/plain"));
        String text = response.asString();
        for (String blocked :
                new String[] {"/members", "/songs/", "/users", "/edit", "/account", "/login"}) {
            assertTrue(text.contains("Disallow: " + blocked + "\n"), blocked);
        }
        assertTrue(text.contains("Sitemap: " + BASE + "/sitemap.xml"));
        assertFalse(text.contains("Disallow: /\n"), "the public pages must stay open");
    }

    @Test
    void theSitemapListsThePublicPagesAndNotTheOldAboutAddress() {
        Response response = given().get("/sitemap.xml");
        assertEquals(200, response.statusCode());
        assertTrue(response.contentType().startsWith("application/xml"));
        Document xml = Jsoup.parse(response.asString(), "", org.jsoup.parser.Parser.xmlParser());
        var locations = xml.select("url > loc").stream().map(e -> e.text()).toList();
        assertEquals(
                java.util.List.of(
                        BASE + "/",
                        BASE + "/become-a-member",
                        BASE + "/agenda",
                        BASE + "/privacy",
                        BASE + "/cookies",
                        BASE + "/terms"),
                locations);
        assertFalse(response.asString().contains("/about"));
    }

    @Test
    void securityTxtHasAContactAnExpiryAndThePolicy() {
        Response response = given().get("/.well-known/security.txt");
        assertEquals(200, response.statusCode());
        assertTrue(response.contentType().startsWith("text/plain"));
        String text = response.asString();
        assertTrue(text.contains("Contact: mailto:"));
        assertTrue(
                text.matches("(?s).*Expires: 20\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ\\n.*"));
        assertTrue(text.contains("Policy: https://github.com/"));
        assertTrue(text.contains("Canonical: " + BASE + "/.well-known/security.txt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/members", "/users", "/edit", "/edit/about", "/account"})
    void securePagesAreNotIndexedWhetherOrNotYouAreLoggedIn(String path) {
        assertEquals(
                "noindex, nofollow",
                given().redirects().follow(false).get(path).header("X-Robots-Tag"),
                "anonymous " + path);
        assertEquals(
                "noindex, nofollow",
                given().redirects()
                        .follow(false)
                        .header("Cookie", accounts.cookie(Role.ADMIN))
                        .get(path)
                        .header("X-Robots-Tag"),
                "admin " + path);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"/login", "/forgot-password", "/does-not-exist", "/test-only/errors/crash"})
    void loginPagesAndErrorPagesAreNotIndexed(String path) {
        assertEquals("noindex, nofollow", given().get(path).header("X-Robots-Tag"), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/become-a-member", "/agenda", "/privacy", "/cookies", "/terms"})
    void publicPagesAreIndexableHaveACanonicalAddressAndSharingTags(String path) {
        Response response = given().accept("text/html").get(path);
        assertNull(response.header("X-Robots-Tag"), path);
        Document page = Jsoup.parse(response.asString());
        assertEquals(BASE + path, page.selectFirst("link[rel=canonical]").attr("href"));
        assertEquals(BASE + path, meta(page, "og:url"));
        assertEquals("website", meta(page, "og:type"));
        assertEquals(page.title(), meta(page, "og:title"));
        assertFalse(meta(page, "og:description").isBlank());
        assertEquals(BASE + "/images/social.png", meta(page, "og:image"));
    }

    @Test
    void securePagesHaveNoCanonicalAddress() {
        Document page =
                Jsoup.parse(
                        given().accept("text/html")
                                .header("Cookie", accounts.cookie(Role.MEMBER))
                                .get("/members")
                                .asString());
        assertTrue(page.select("link[rel=canonical]").isEmpty());
        assertNotNull(meta(page, "og:image"));
    }

    @Test
    void theDefaultSharingPictureIsServed() {
        Response response = given().get("/images/social.png");
        assertEquals(200, response.statusCode());
        assertTrue(response.contentType().startsWith("image/png"));
        assertTrue(response.asByteArray().length > 100);
    }

    private static String meta(Document page, String property) {
        var element = page.selectFirst("meta[property='" + property + "']");
        assertNotNull(element, property);
        return element.attr("content");
    }
}
