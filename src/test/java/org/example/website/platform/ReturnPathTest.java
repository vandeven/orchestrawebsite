package org.example.website.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.example.website.platform.api.request.ReturnPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** LOG-6: the page to return to must be a path on this website. No open redirect. */
class ReturnPathTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/",
                "/members",
                "/songs/manage",
                "/members?song=12&x=1",
                "/a/b/c.html#top",
                "/login?return=/members"
            })
    void acceptsPathsOnThisWebsite(String value) {
        assertEquals(Optional.of(value), ReturnPath.sanitize(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "//example.org",
                "///example.org",
                "https://example.org",
                "http://example.org/members",
                "javascript:alert(1)",
                "data:text/html,x",
                "example.org",
                "members",
                "/\\example.org",
                "\\\\example.org",
                "/members\\..\\x",
                "/%2f%2fexample.org",
                "/%5cexample.org",
                "/members\r\nSet-Cookie: x=1",
                "/members\nLocation: https://example.org",
                "/me mbers",
                "/members\u0000",
                "/members\t",
                " /members",
                "/\u007fmembers"
            })
    void refusesEverythingElse(String value) {
        assertEquals(Optional.empty(), ReturnPath.sanitize(value));
    }

    @Test
    void lengthBoundary() {
        String atLimit = "/" + "a".repeat(ReturnPath.MAX_LENGTH - 1);
        assertTrue(ReturnPath.sanitize(atLimit).isPresent());
        assertEquals(Optional.empty(), ReturnPath.sanitize(atLimit + "a"));
    }
}
