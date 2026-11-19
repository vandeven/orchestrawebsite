package org.example.website.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.example.website.platform.api.Email;
import org.example.website.platform.api.EmailLayout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** DES-11: one simple email template, with a plain text and an HTML version. */
class EmailLayoutTest {

    private static final String LINK = "https://example.org/reset-password?token=abc123&x=1";

    private final String html =
            EmailLayout.html(
                    "The Orchestra",
                    "Choose your password",
                    List.of("First.", "Second."),
                    "Choose it",
                    LINK,
                    "Footer text");
    private final String text =
            EmailLayout.text(
                    "The Orchestra",
                    "Choose your password",
                    List.of("First.", "Second."),
                    "Choose it",
                    LINK,
                    "Footer text");

    @Test
    void theHtmlVersionHasNoScriptNoRemoteImageAndNoTracking() {
        Document page = Jsoup.parse(html);
        assertTrue(page.select("script, iframe, object, embed, form, link, style").isEmpty());
        assertTrue(page.select("img").isEmpty(), "nothing is lost when images are switched off");
        assertFalse(html.contains("url("), "no remote background either");
        for (var element : page.getAllElements()) {
            element.attributes().forEach(a -> assertFalse(a.getKey().startsWith("on")));
        }
    }

    @Test
    void theHtmlVersionUsesSystemFontsAndInlineStyles() {
        assertTrue(html.contains("system-ui"));
        assertFalse(html.contains("@font-face"));
        assertFalse(html.contains("fonts.googleapis"));
        assertTrue(Jsoup.parse(html).selectFirst("body").hasAttr("style"));
    }

    @Test
    void theLinkIsAButtonAndAlsoWrittenOut() {
        Document page = Jsoup.parse(html);
        assertEquals(LINK, page.selectFirst("a[href]").attr("href"));
        assertEquals("Choose it", page.selectFirst("a[href]").text());
        assertTrue(page.text().contains(LINK), "for programs that do not show buttons");
    }

    @Test
    void bothVersionsHaveTheNameTheTextAndTheFooter() {
        for (String body : List.of(Jsoup.parse(html).text(), text)) {
            assertTrue(body.contains("The Orchestra"));
            assertTrue(body.contains("Choose your password"));
            assertTrue(body.contains("First."));
            assertTrue(body.contains("Second."));
            assertTrue(body.contains("Footer text"));
        }
        assertTrue(text.contains(LINK));
    }

    @Test
    void everythingFromOutsideIsEscaped() {
        String evil = "<script>alert(1)</script>\"'&";
        String page =
                EmailLayout.html(
                        evil,
                        evil,
                        List.of(evil),
                        evil,
                        "https://example.org/?a=\"><script>",
                        evil);
        assertFalse(page.contains("<script"));
        assertTrue(
                page.contains("href=\"https://example.org/?a=&quot;&gt;&lt;script&gt;\""),
                "the address is escaped inside the attribute");
        assertTrue(page.contains("&lt;script&gt;"));
        assertEquals(0, Jsoup.parse(page).select("script").size());
    }

    @Test
    void aMailWithoutALinkLeavesItOut() {
        String plain = EmailLayout.text("N", "H", List.of("P"), null, null, "F");
        assertFalse(plain.contains("null"));
        assertTrue(
                Jsoup.parse(EmailLayout.html("N", "H", List.of("P"), null, null, "F"))
                        .select("a")
                        .isEmpty());
    }

    // The email itself (HRD-7: no header injection)

    @ParameterizedTest
    @ValueSource(
            strings = {"a@example.org\r\nBcc: x@example.org", "a@example.org\nBcc: x", "", " "})
    void anAddressWithLineBreaksOrNothingIsRefused(String to) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Email(to, "Subject", "text", "<p>html</p>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Subject\r\nBcc: x@example.org", "Subject\nX: y", "", " "})
    void aSubjectWithLineBreaksOrNothingIsRefused(String subject) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Email("a@example.org", subject, "text", "<p>html</p>"));
    }

    @Test
    void everyEmailHasBothVersions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Email("a@example.org", "S", "", "<p>x</p>"));
        assertThrows(
                IllegalArgumentException.class, () -> new Email("a@example.org", "S", "x", null));
        assertEquals("S", new Email("a@example.org", "S", "x", "<p>x</p>").subject());
    }
}
