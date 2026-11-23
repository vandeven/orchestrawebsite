package org.example.website.content.internal.html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The safe HTML allowlist (EDT-4, EDT-6, EDT-14, HRD-5): harmful inputs go, formatting stays. */
class HtmlCleanerTest {

    private static final String IMAGES = "https://images.example.test/";
    private final HtmlCleaner cleaner = new HtmlCleaner(IMAGES);

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<script>alert(1)</script>",
                "<img src=\"https://images.example.test/a.jpg\" onerror=\"alert(1)\">",
                "<p onclick=\"alert(1)\">x</p>",
                "<a href=\"javascript:alert(1)\">x</a>",
                "<a href=\"JaVaScRiPt:alert(1)\">x</a>",
                "<a href=\"data:text/html;base64,PHNjcmlwdD4=\">x</a>",
                "<a href=\"vbscript:x\">x</a>",
                "<iframe src=\"https://evil.example\"></iframe>",
                "<object data=\"x\"></object>",
                "<embed src=\"x\">",
                "<form action=\"https://evil.example\"><input name=a></form>",
                "<p style=\"color:red\">x</p>",
                "<style>p{color:red}</style>",
                "<svg onload=\"alert(1)\"></svg>",
                "<math><mi xlink:href=\"javascript:alert(1)\">x</mi></math>",
                "<img src=\"https://evil.example/a.jpg\">",
                "<img src=\"//evil.example/a.jpg\">",
                "<img src=\"data:image/png;base64,AAAA\">",
                "<img src=\"https://images.example.test.evil.example/a.jpg\">",
                "<base href=\"https://evil.example/\">",
                "<meta http-equiv=\"refresh\" content=\"0;url=https://evil.example\">",
                "<link rel=\"stylesheet\" href=\"https://evil.example/x.css\">",
                "<div class=\"x\">x</div><font color=\"red\">x</font>"
            })
    void harmfulInputIsRemoved(String input) {
        String clean = cleaner.clean(input).toLowerCase();
        for (String bad :
                new String[] {
                    "<script",
                    "onerror",
                    "onclick",
                    "onload",
                    "javascript:",
                    "data:",
                    "vbscript",
                    "<iframe",
                    "<object",
                    "<embed",
                    "<form",
                    "<input",
                    "style",
                    "<svg",
                    "<math",
                    "evil.example",
                    "<base",
                    "<meta",
                    "<link",
                    "<font",
                    "color="
                }) {
            assertFalse(clean.contains(bad), bad + " is still in: " + clean);
        }
    }

    @Test
    void allFormattingTheEditorCanMakeIsKept() {
        String html =
                "<h2>Two</h2><h3>Three</h3><h4>Four</h4>"
                        + "<p class=\"align-center\">a <strong>b</strong> <em>c</em> <u>d</u> <s>e</s></p>"
                        + "<ul><li>x</li></ul><ol><li>y</li></ol>"
                        + "<blockquote>q</blockquote><hr />"
                        + "<table><thead><tr><th scope=\"col\">h</th></tr></thead>"
                        + "<tbody><tr><td colspan=\"2\">c</td></tr></tbody></table>";
        assertEquals(html, cleaner.clean(html).replace("\n", ""));
    }

    @Test
    void imagesFromTheOwnStorageKeepTheirDetails() {
        String clean =
                cleaner.clean(
                        "<img src=\"https://images.example.test/a1.webp\" alt=\"The band\""
                                + " width=\"800\" height=\"600\" class=\"img-medium align-left\">");
        assertTrue(clean.contains("src=\"https://images.example.test/a1.webp\""), clean);
        assertTrue(clean.contains("alt=\"The band\""), clean);
        assertTrue(clean.contains("width=\"800\""), clean);
        assertTrue(clean.contains("class=\"img-medium align-left\""), clean);
    }

    @Test
    void anImageWithEmptyAltTextStaysDecorative() {
        assertTrue(
                cleaner.clean("<img src=\"https://images.example.test/a.jpg\" alt=\"\">")
                        .contains("alt=\"\""));
    }

    @Test
    void anyOtherClassIsRemoved() {
        String clean = cleaner.clean("<p class=\"align-left evil\">x</p><p class=\"btn\">y</p>");
        assertFalse(clean.contains("evil"), clean);
        assertFalse(clean.contains("btn"), clean);
    }

    @Test
    void linksToOtherSitesOpenInANewTabWithoutAnOpener() {
        String clean = cleaner.clean("<a href=\"https://example.org/x\">x</a>");
        assertTrue(clean.contains("target=\"_blank\""), clean);
        assertTrue(clean.contains("noopener"), clean);
        assertTrue(clean.contains("noreferrer"), clean);
    }

    @Test
    void emailLinksStay() {
        // The sanitizer writes @ as a character reference, so read the attribute back
        var link =
                org.jsoup.Jsoup.parse(cleaner.clean("<a href=\"mailto:info@example.org\">mail</a>"))
                        .selectFirst("a");
        assertEquals("mailto:info@example.org", link.attr("href"));
    }

    @Test
    void aWebsiteWithoutAnImageStorageAllowsNoImages() {
        assertFalse(
                new HtmlCleaner("")
                        .clean("<img src=\"https://images.example.test/a.jpg\">")
                        .contains("<img"));
    }

    @Test
    void nothingBecomesEmptyAndNullBecomesEmpty() {
        assertEquals("", cleaner.clean(null));
        assertEquals("", cleaner.clean(""));
    }

    @Test
    void cleaningTwiceChangesNothing() {
        String once = cleaner.clean("<p>x<script>y</script><b onclick=1>z</b></p>");
        assertEquals(once, cleaner.clean(once));
    }
}
