package org.example.website.platform.internal.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Validation rules for the platform configuration: valid, boundary and invalid input. */
class ConfigRulesTest {

    private static final String GOOD_URL = "https://example.org";

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://example.org",
                "https://www.example.org",
                "https://example.org:8443",
                "https://my-run-service-abc123-ez.a.run.app"
            })
    void acceptsHttpsAddressesWithOnlyAHost(String url) {
        assertTrue(ConfigRules.validBaseUrl(url, false));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "http://example.org",
                "example.org",
                "https://",
                "https:///path",
                "https://example.org/",
                "https://example.org/path",
                "https://example.org?x=1",
                "https://example.org#top",
                "https://user@example.org",
                "https://user:secret@example.org",
                "ftp://example.org",
                "javascript:alert(1)",
                "https://exa mple.org",
                "http://localhost",
                "http://localhost:8080"
            })
    void refusesEverythingElse(String url) {
        assertFalse(ConfigRules.validBaseUrl(url, false));
    }

    @Test
    void localAddressIsOnlyAcceptedWhenAllowed() {
        assertTrue(ConfigRules.validBaseUrl("http://localhost:8080", true));
        assertFalse(ConfigRules.validBaseUrl("http://localhost:8080", false));
        assertFalse(ConfigRules.validBaseUrl("http://example.org", true));
        assertFalse(ConfigRules.validBaseUrl("http://localhost.evil.org", true));
    }

    @Test
    void baseUrlLengthBoundary() {
        String suffix = ".org";
        String atLimit = "https://" + "a".repeat(200 - 8 - suffix.length()) + suffix;
        assertEquals(200, atLimit.length());
        assertTrue(ConfigRules.validBaseUrl(atLimit, false));
        assertFalse(ConfigRules.validBaseUrl("https://a" + atLimit.substring(8), false));
    }

    @Test
    void nameLengthBoundary() {
        assertTrue(ConfigRules.problems("a", GOOD_URL, "INFO", false, 0).isEmpty());
        assertTrue(ConfigRules.problems("a".repeat(100), GOOD_URL, "INFO", false, 0).isEmpty());
        assertEquals(1, ConfigRules.problems("a".repeat(101), GOOD_URL, "INFO", false, 0).size());
        assertEquals(1, ConfigRules.problems("", GOOD_URL, "INFO", false, 0).size());
        assertEquals(1, ConfigRules.problems("   ", GOOD_URL, "INFO", false, 0).size());
        assertEquals(1, ConfigRules.problems(null, GOOD_URL, "INFO", false, 0).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEBUG", "INFO", "WARNING", "ERROR", "debug", "Info", " warning "})
    void acceptsTheFourLogLevelsInAnyCase(String level) {
        assertTrue(ConfigRules.problems("Name", GOOD_URL, level, false, 0).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"TRACE", "WARN", "FATAL", "OFF", "ALL", "verbose", "INFO;rm"})
    void refusesOtherLogLevels(String level) {
        assertEquals(1, ConfigRules.problems("Name", GOOD_URL, level, false, 0).size());
    }

    @Test
    void debugBecomesInfoInProductionOnly() {
        assertEquals(
                new ConfigRules.LogLevel("INFO", true), ConfigRules.resolveLogLevel("debug", true));
        assertEquals(
                new ConfigRules.LogLevel("DEBUG", false),
                ConfigRules.resolveLogLevel("DEBUG", false));
        assertEquals(
                new ConfigRules.LogLevel("ERROR", false),
                ConfigRules.resolveLogLevel("error", true));
    }

    @Test
    void proxyHopsBoundary() {
        for (int hops : new int[] {0, 1, 2}) {
            assertTrue(ConfigRules.problems("Name", GOOD_URL, "INFO", false, hops).isEmpty());
        }
        assertEquals(1, ConfigRules.problems("Name", GOOD_URL, "INFO", false, -1).size());
        assertEquals(1, ConfigRules.problems("Name", GOOD_URL, "INFO", false, 3).size());
    }

    @Test
    void reportsEveryProblemWithoutShowingValues() {
        var problems = ConfigRules.problems("", "http://secret-host.example", "LOUD", false, 0);
        assertEquals(3, problems.size());
        problems.forEach(p -> {
            assertFalse(p.contains("secret-host"));
            assertFalse(p.contains("LOUD"));
        });
    }
}
