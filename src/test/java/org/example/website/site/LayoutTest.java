package org.example.website.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.Role;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The shared layout (DES-6, DES-7), the menu per role (plan: Layout and navigation) and the rules
 * for page markup.
 */
@QuarkusTest
class LayoutTest {

    @Inject TestAccounts accounts;

    private Document page(String path, String level) {
        var request = given().accept("text/html");
        if (level != null) {
            request = request.header("Cookie", accounts.cookie(Role.valueOf(level)));
        }
        return Jsoup.parse(request.get(path).then().extract().asString());
    }

    private static List<String> menuLabels(Document page) {
        return page.select("nav[aria-label='Main menu'] .site-nav-list li").stream()
                .map(Element::text)
                .toList();
    }

    @Test
    void anonymousVisitorsSeeThePublicItemsAndLogIn() {
        assertEquals(
                List.of("About", "Become a member", "Agenda", "Log in"),
                menuLabels(page("/", null)));
    }

    @Test
    void membersSeeTheMemberItemsAndLogOut() {
        assertEquals(
                List.of(
                        "About",
                        "Become a member",
                        "Agenda",
                        "Member page",
                        "My account",
                        "Log out"),
                menuLabels(page("/", "MEMBER")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MASTER"})
    void adminsAndTheMasterSeeTheManagementItems(String level) {
        assertEquals(
                List.of(
                        "About",
                        "Become a member",
                        "Agenda",
                        "Member page",
                        "Song management",
                        "Edit pages",
                        "User management",
                        "My account",
                        "Log out"),
                menuLabels(page("/", level)));
    }

    @Test
    void logOutIsAPostFormNeverALink() {
        Document page = page("/", "MEMBER");
        Element form = page.selectFirst("form.site-nav-form");
        assertNotNull(form);
        assertEquals("post", form.attr("method"));
        assertEquals("/logout", form.attr("action"));
        assertTrue(page.select("a[href='/logout']").isEmpty(), "logging out is never a GET link");
    }

    @Test
    void theCurrentPageIsMarked() {
        Document page = page("/", null);
        assertEquals(1, page.select("[aria-current=page]").size());
        assertEquals("About", page.selectFirst("[aria-current=page]").text());
    }

    @ParameterizedTest
    @CsvSource({"/", "/does-not-exist", "/test-only/errors/crash"})
    void everyPageHasTheBasics(String path) {
        Document page = page(path, null);
        assertEquals("en", page.selectFirst("html").attr("lang"));
        assertFalse(page.title().isBlank(), "a page title");
        assertTrue(page.title().contains("PLACEHOLDER_NAME"));
        assertNotNull(page.selectFirst("meta[name=viewport]"));
        assertEquals(1, page.select("h1").size(), "one main heading");
        assertNotNull(page.selectFirst("main"), "a main landmark");
        assertNotNull(page.selectFirst("link[rel=stylesheet][href='/css/tokens.css']"));
        assertNotNull(page.selectFirst("link[rel=stylesheet][href='/css/base.css']"));
        assertNotNull(page.selectFirst("link[rel=icon]"));
    }

    @Test
    void thePageHasASkipLinkAsTheFirstFocusableElement() {
        Document page = page("/", null);
        Element first = page.selectFirst("a, button, input, select, textarea");
        assertEquals("Skip to content", first.text());
        assertEquals("#content", first.attr("href"));
        assertNotNull(page.getElementById("content"), "the skip link has a target");
        assertEquals("main", page.getElementById("content").tagName());
    }

    @Test
    void theMenuIsANavigationLandmarkWithAButtonForPhones() {
        Document page = page("/", null);
        Element nav = page.selectFirst("nav[aria-label='Main menu']");
        assertNotNull(nav);
        Element toggle = nav.selectFirst("button[aria-controls=main-menu]");
        assertNotNull(toggle);
        assertEquals("false", toggle.attr("aria-expanded"));
        assertEquals("button", toggle.attr("type"));
        assertNotNull(page.getElementById("main-menu"));
    }

    @Test
    void theFooterHasTheNameAndTheLegalPages() {
        Document page = page("/", null);
        Element footer = page.selectFirst("footer");
        assertTrue(footer.text().contains("PLACEHOLDER_NAME"));
        assertEquals(
                List.of("/privacy", "/cookies", "/terms"),
                footer.select("a").stream().map(a -> a.attr("href")).toList());
    }

    @ParameterizedTest
    @CsvSource({"/", "/does-not-exist", "/test-only/errors/crash"})
    void pagesLoadNothingFromAnotherSiteAndHaveNoInlineCodeOrStyles(String path) {
        Document page = page(path, "ADMIN");
        for (Element element : page.select("[src], [href], [action]")) {
            for (String attribute : List.of("src", "href", "action")) {
                String value = element.attr(attribute);
                if (value.isEmpty()) {
                    continue;
                }
                assertTrue(
                        value.startsWith("/") && !value.startsWith("//") || value.startsWith("#"),
                        attribute + " stays on this website: " + value);
            }
        }
        assertTrue(page.select("style").isEmpty(), "no style blocks (CSP: style-src 'self')");
        assertTrue(page.select("[style]").isEmpty(), "no style attributes");
        assertTrue(
                page.select("script").stream()
                        .allMatch(s -> s.hasAttr("src") && s.data().isBlank()),
                "no inline scripts (CSP: script-src 'self')");
        for (Element element : page.getAllElements()) {
            element.attributes()
                    .forEach(a ->
                            assertFalse(
                                    a.getKey().startsWith("on"), "no event handler attributes"));
        }
        assertTrue(page.select("iframe, object, embed, link[rel=preconnect]").isEmpty());
    }

    @Test
    void theErrorPagesUseTheSameStylesAsTheRestOfTheWebsite() {
        Document page = page("/test-only/errors/denied", null);
        assertNotNull(page.selectFirst("link[href='/css/base.css']"));
        assertEquals("Access denied", page.selectFirst("h1").text());
    }

    @Test
    void thePageStaysUsableWithoutJavaScript() {
        // The menu list is in the markup and visible by default; the script only adds a button
        // behavior and the "js" class that collapses the list on phones.
        Document page = page("/", null);
        assertEquals(4, page.select(".site-nav-list a").size());
        assertEquals("/js/menu.js", page.selectFirst("script[src]").attr("src"));
    }
}
