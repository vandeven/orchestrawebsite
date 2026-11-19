package org.example.website.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** DES-2 and DES-3: the design tokens are defined once and every color pair is accessible. */
class DesignTokensTest {

    private static final Pattern COLOR_TOKEN =
            Pattern.compile("(--color-[a-z-]+)\\s*:\\s*(#[0-9a-fA-F]{6})\\s*;");

    /** foreground, background, minimum contrast ratio, and what the pair is used for. */
    record Pair(String foreground, String background, double minimum, String use) {}

    private static final List<Pair> PAIRS =
            List.of(
                    new Pair("--color-text", "--color-bg", 4.5, "body text"),
                    new Pair("--color-text", "--color-surface", 4.5, "text in cards and forms"),
                    new Pair("--color-text-muted", "--color-bg", 4.5, "muted text"),
                    new Pair("--color-text-muted", "--color-surface", 4.5, "muted text in cards"),
                    new Pair("--color-link", "--color-bg", 4.5, "links"),
                    new Pair("--color-link", "--color-surface", 4.5, "links in cards"),
                    new Pair("--color-on-brand", "--color-brand", 4.5, "button text"),
                    new Pair("--color-on-brand", "--color-brand-dark", 4.5, "button text on hover"),
                    new Pair("--color-success", "--color-success-bg", 4.5, "success message"),
                    new Pair("--color-warning", "--color-warning-bg", 4.5, "warning message"),
                    new Pair("--color-error", "--color-error-bg", 4.5, "error message"),
                    new Pair("--color-error", "--color-bg", 4.5, "error text in a form"),
                    new Pair("--color-error", "--color-surface", 4.5, "error text in a card"),
                    new Pair("--color-border", "--color-bg", 3.0, "borders of fields and buttons"),
                    new Pair("--color-border", "--color-surface", 3.0, "borders in cards"),
                    new Pair("--color-focus", "--color-bg", 3.0, "focus marker"),
                    new Pair("--color-focus", "--color-surface", 3.0, "focus marker in cards"),
                    new Pair("--color-brand", "--color-bg", 3.0, "brand color as a component"));

    static String css(String name) throws IOException {
        try (InputStream in =
                DesignTokensTest.class.getResourceAsStream("/META-INF/resources/css/" + name)) {
            assertTrue(in != null, name + " exists");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> colors(String block) {
        Map<String, String> colors = new LinkedHashMap<>();
        Matcher matcher = COLOR_TOKEN.matcher(block);
        while (matcher.find()) {
            colors.put(matcher.group(1), matcher.group(2));
        }
        return colors;
    }

    private static String[] lightAndDark() throws IOException {
        String tokens = css("tokens.css");
        int dark = tokens.indexOf("@media (prefers-color-scheme: dark)");
        assertTrue(dark > 0, "dark mode tokens exist (DES-3)");
        return new String[] {tokens.substring(0, dark), tokens.substring(dark)};
    }

    /** WCAG relative luminance of a #rrggbb color. */
    static double luminance(String hex) {
        double[] channel = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            channel[i] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channel[0] + 0.7152 * channel[1] + 0.0722 * channel[2];
    }

    static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static List<String> failures(Map<String, String> colors, String mode) {
        List<String> failures = new ArrayList<>();
        for (Pair pair : PAIRS) {
            String foreground = colors.get(pair.foreground());
            String background = colors.get(pair.background());
            assertTrue(foreground != null, pair.foreground() + " is defined in " + mode + " mode");
            assertTrue(background != null, pair.background() + " is defined in " + mode + " mode");
            double ratio = contrast(foreground, background);
            if (ratio < pair.minimum()) {
                failures.add(
                        "%s mode, %s (%s on %s): %.2f is below %.1f"
                                .formatted(
                                        mode,
                                        pair.use(),
                                        pair.foreground(),
                                        pair.background(),
                                        ratio,
                                        pair.minimum()));
            }
        }
        return failures;
    }

    @Test
    void everyColorPairIsAccessibleInLightMode() throws IOException {
        assertEquals(List.of(), failures(colors(lightAndDark()[0]), "light"));
    }

    @Test
    void everyColorPairIsAccessibleInDarkMode() throws IOException {
        Map<String, String> dark = colors(lightAndDark()[0]);
        dark.putAll(colors(lightAndDark()[1])); // dark mode overrides the light tokens
        assertEquals(List.of(), failures(dark, "dark"));
    }

    @Test
    void darkModeDefinesEveryColorToken() throws IOException {
        Map<String, String> light = colors(lightAndDark()[0]);
        Map<String, String> dark = colors(lightAndDark()[1]);
        assertEquals(light.keySet(), dark.keySet(), "dark mode has a full set of color tokens");
    }

    @Test
    void theContrastCalculationIsCorrect() {
        assertEquals(21.0, contrast("#000000", "#ffffff"), 0.01);
        assertEquals(1.0, contrast("#777777", "#777777"), 0.001);
        assertEquals(4.48, contrast("#777777", "#ffffff"), 0.02); // the well known 4.5 borderline
    }

    @Test
    void textIsAtLeastSixteenPixelsAndScalesWithTheBrowserSettings() throws IOException {
        String tokens = css("tokens.css");
        assertTrue(tokens.contains("--text-base: 1rem;"), "the base size is 1rem (16 px)");
        assertTrue(
                !Pattern.compile("font-size\\s*:\\s*\\d+px").matcher(css("base.css")).find(),
                "font sizes are not fixed in pixels");
    }
}
