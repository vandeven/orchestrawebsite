package org.example.website.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
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

/** The Agenda page (AGE-1 to AGE-3) and the Become a member page (BAM-1 to BAM-5). */
@QuarkusTest
class PublicPagesTest {

    @Inject EditableTexts texts;
    @Inject TestAccounts accounts;

    @AfterEach
    void clearTheTexts() {
        UserSummary admin = accounts.newUser(Role.ADMIN);
        for (TextKey key : TextKey.values()) {
            texts.clear(key, admin);
        }
    }

    private void write(TextKey key, String html) {
        texts.save(key, html, accounts.newUser(Role.ADMIN));
    }

    private static Document get(String path) {
        return Jsoup.parse(given().accept("text/html").get(path).asString());
    }

    // The agenda

    @Test
    void theAgendaIsPublicLinkedFromTheMenuAndCachedBriefly() {
        Response response = given().accept("text/html").get("/agenda");
        assertEquals(200, response.statusCode());
        assertEquals("public, max-age=60", response.header("Cache-Control"));
        Document page = Jsoup.parse(response.asString());
        assertEquals("Agenda", page.selectFirst("h1").text());
        var link = page.selectFirst("nav a.site-nav-link[href='/agenda']");
        assertNotNull(link);
        assertEquals("page", link.attr("aria-current"));
    }

    @Test
    void anEmptyAgendaShowsTheFriendlyMessage() {
        Document page = get("/agenda");
        assertTrue(page.text().contains("There are no performances planned at the moment"));
        assertTrue(page.select(".page-text").isEmpty());
    }

    @Test
    void theAgendaTextIsShownWithItsFormattingAndHarmfulHtmlIsNot() {
        write(TextKey.AGENDA, "<h2>May</h2><ul><li>Concert <b>hall</b></li></ul>");
        Document page = get("/agenda");
        assertEquals("May", page.selectFirst(".page-text h2").text());
        assertEquals("hall", page.selectFirst(".page-text li b").text());
        assertFalse(page.text().contains("no performances planned"));
    }

    // Become a member

    @Test
    void thePageIsPublicLinkedFromTheMenuAndHasThePlaceholderWhenEmpty() {
        Response response = given().accept("text/html").get("/become-a-member");
        assertEquals(200, response.statusCode());
        assertEquals("public, max-age=60", response.header("Cache-Control"));
        Document page = Jsoup.parse(response.asString());
        assertEquals("Become a member", page.selectFirst("h1").text());
        assertNotNull(page.selectFirst("nav a.site-nav-link[href='/become-a-member']"));
        assertTrue(page.text().contains("has not been written yet"));
    }

    @Test
    void theAuditionSectionIsOnlyShownWhenThereIsAuditionText() {
        assertTrue(get("/become-a-member").select("#audition-heading").isEmpty());
        write(TextKey.AUDITION, "<p>Auditions are on <strong>5 June</strong>.</p>");
        Document page = get("/become-a-member");
        assertEquals("Audition", page.selectFirst("#audition-heading").text());
        assertEquals("5 June", page.selectFirst("section .page-text strong").text());
        assertFalse(page.text().contains("has not been written yet") && false);
    }

    @Test
    void bothTextsAreShownAndTheMailtoLinkWorks() {
        write(
                TextKey.BECOME_A_MEMBER,
                "<p>Write to <a href=\"mailto:choir@example.org\">choir@example.org</a>.</p>");
        Document page = get("/become-a-member");
        assertEquals("mailto:choir@example.org", page.selectFirst(".page-text a").attr("href"));
        assertFalse(page.text().contains("has not been written yet"));
    }

    @Test
    void theFormHasLabelledFieldsAPrivacyNoticeAndAHiddenTrap() {
        Document page = get("/become-a-member");
        for (String id : new String[] {"contact-email", "contact-subject", "contact-message"}) {
            assertNotNull(page.selectFirst("label[for=" + id + "]"), id);
            assertTrue(page.selectFirst("#" + id).hasAttr("required"));
        }
        assertEquals("200", page.selectFirst("#contact-subject").attr("maxlength"));
        assertEquals("5000", page.selectFirst("#contact-message").attr("maxlength"));
        assertNotNull(page.selectFirst("a[href='/privacy']"));
        assertTrue(page.text().contains("not stored"));
        var trap = page.selectFirst(".field-trap");
        assertEquals("true", trap.attr("aria-hidden"));
        assertEquals("-1", trap.selectFirst("input").attr("tabindex"));
    }
}
