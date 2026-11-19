package org.example.website.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** DES-2, DES-6, DES-7, DES-8, DES-10: rules every stylesheet has to follow. */
class StylesheetTest {

    private static final Pattern FIXED_COLOR =
            Pattern.compile(
                    "#[0-9a-fA-F]{3,8}\\b|\\b(?:rgb|rgba|hsl|hsla|hwb|lab|lch|oklab|oklch|color)\\(");

    /**
     * Color names that must not be used outside the tokens (system keywords like CanvasText are
     * fine).
     */
    private static final Pattern COLOR_NAME =
            Pattern.compile(
                    "(?<![-\\w])(?:white|black|red|green|blue|yellow|orange|purple|pink|gray|grey|brown|navy|teal|maroon|silver|gold)(?![-\\w])");

    @Test
    void noStylesheetOutsideTheTokensUsesAFixedColor() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String file : List.of("base.css")) {
            String css = DesignTokensTest.css(file).replaceAll("(?s)/\\*.*?\\*/", "");
            for (String line : css.split("\n")) {
                if (FIXED_COLOR.matcher(line).find() || COLOR_NAME.matcher(line).find()) {
                    violations.add(file + ": " + line.trim());
                }
            }
        }
        assertEquals(List.of(), violations, "colors belong in tokens.css (DES-2)");
    }

    @Test
    void theDetectionOfFixedColorsWorks() {
        assertTrue(FIXED_COLOR.matcher("color: #fff;").find());
        assertTrue(FIXED_COLOR.matcher("background: rgb(0 0 0 / 60%);").find());
        assertTrue(COLOR_NAME.matcher("color: white;").find());
        assertFalse(FIXED_COLOR.matcher("color: var(--color-text);").find());
        assertFalse(COLOR_NAME.matcher("border: 1px solid var(--color-border);").find());
        assertFalse(COLOR_NAME.matcher("color: CanvasText;").find());
    }

    @Test
    void stylesheetsLoadNothingFromAnotherSite() throws IOException {
        for (String file : List.of("tokens.css", "base.css")) {
            String css = DesignTokensTest.css(file);
            assertFalse(css.contains("@import"), file + " has no @import");
            assertFalse(css.contains("@font-face"), file + " loads no font files (DES-4)");
            assertFalse(
                    Pattern.compile("url\\(\\s*['\"]?(https?:)?//").matcher(css).find(),
                    file + " loads nothing from another domain");
        }
    }

    @Test
    void focusIsAlwaysVisible() throws IOException {
        String css = DesignTokensTest.css("base.css");
        assertTrue(css.contains(":focus-visible"), "there is a focus marker");
        assertTrue(css.contains("var(--color-focus)"));
        // The only element that may hide its outline is the main area, which the skip link focuses.
        Matcher matcher =
                Pattern.compile("([^{}]+)\\{[^{}]*outline\\s*:\\s*(none|0)[^{}]*\\}").matcher(css);
        List<String> selectors = new ArrayList<>();
        while (matcher.find()) {
            selectors.add(matcher.group(1).trim());
        }
        assertEquals(List.of(".site-main:focus"), selectors);
    }

    @Test
    void touchTargetsUseTheMinimumSize() throws IOException {
        String tokens = DesignTokensTest.css("tokens.css");
        assertTrue(tokens.contains("--target-size: 2.75rem"), "44 px");
        String css = DesignTokensTest.css("base.css");
        for (String selector :
                List.of(
                        ".button",
                        ".site-nav-link",
                        ".site-nav-button",
                        ".site-nav-toggle",
                        "select")) {
            Matcher matcher =
                    Pattern.compile(Pattern.quote(selector) + "[^{}]*\\{([^{}]*)\\}").matcher(css);
            boolean found = false;
            while (matcher.find()) {
                found |= matcher.group(1).contains("min-height: var(--target-size)");
            }
            assertTrue(found, selector + " is at least 44 px high");
        }
    }

    @Test
    void motionPrintAndColorSchemeAreHandled() throws IOException {
        String css = DesignTokensTest.css("base.css");
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"), "DES-8");
        assertTrue(
                Pattern.compile(
                                "@media print\\s*\\{[^@]*\\.site-header[^@]*\\.site-footer",
                                Pattern.DOTALL)
                        .matcher(css)
                        .find(),
                "the menu and footer are hidden when printing (DES-10)");
        assertTrue(DesignTokensTest.css("tokens.css").contains("color-scheme: light dark"));
    }

    @Test
    void theSkipLinkIsHiddenUntilItHasFocus() throws IOException {
        String css = DesignTokensTest.css("base.css");
        assertTrue(css.contains(".skip-link:focus"));
    }

    @Test
    void tablesBecomeCardsOnNarrowScreens() throws IOException {
        String css = DesignTokensTest.css("base.css");
        assertTrue(
                Pattern.compile(
                                "@media \\(max-width: 40em\\)\\s*\\{[^@]*\\.table-stack td\\[data-label\\]::before",
                                Pattern.DOTALL)
                        .matcher(css)
                        .find(),
                "a label in front of every value, so nothing needs sideways scrolling");
    }

    @Test
    void theHeaderContentAndFooterShareOnePageWidth() throws IOException {
        String css = DesignTokensTest.css("base.css");
        assertTrue(css.contains("var(--page-width)"));
        assertTrue(DesignTokensTest.css("tokens.css").contains("--page-width"));
        assertTrue(
                css.contains(".site-main > :not(.table-wrap):not(dialog)"),
                "text keeps a readable line length");
    }
}
