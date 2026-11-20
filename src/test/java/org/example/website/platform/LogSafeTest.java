package org.example.website.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.example.website.platform.api.support.LogSafe;
import org.junit.jupiter.api.Test;

/** LGG-5: identifiers of visitors are logged as a short hash. */
class LogSafeTest {

    @Test
    void theSameValueGivesTheSameHashAndOtherValuesDiffer() {
        assertEquals(LogSafe.hash("a@example.org"), LogSafe.hash("a@example.org"));
        assertNotEquals(LogSafe.hash("a@example.org"), LogSafe.hash("b@example.org"));
        assertEquals(12, LogSafe.hash("a@example.org").length());
        assertEquals(LogSafe.hash(""), LogSafe.hash(null));
    }

    @Test
    void theHashDoesNotContainTheValue() {
        assertFalse(LogSafe.hash("someone@example.org").contains("someone"));
    }
}
