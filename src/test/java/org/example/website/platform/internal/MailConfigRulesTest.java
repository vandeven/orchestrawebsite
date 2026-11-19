package org.example.website.platform.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The email configuration is checked at startup. Valid, boundary and invalid input. */
class MailConfigRulesTest {

    private static final String KEY = "k".repeat(20);

    private static List<String> check(
            String provider,
            boolean allowOutbox,
            boolean production,
            String from,
            String name,
            String replyTo,
            String key) {
        return MailConfigRules.problems(
                provider,
                allowOutbox,
                production,
                from,
                name,
                Optional.ofNullable(replyTo),
                Optional.ofNullable(key),
                "https://api.brevo.com/v3/smtp/email");
    }

    @Test
    void validConfigurations() {
        assertEquals(
                List.of(),
                check(
                        "brevo",
                        false,
                        true,
                        "no-reply@example.org",
                        "The Orchestra",
                        "b@example.org",
                        KEY));
        assertEquals(
                List.of(), check("outbox", false, false, "no-reply@example.org", "N", null, null));
        assertEquals(
                List.of(), check("outbox", true, true, "no-reply@example.org", "N", null, null));
    }

    @Test
    void theProviderMustBeKnown() {
        assertEquals(1, check("smtp", false, false, "a@example.org", "N", null, null).size());
        assertEquals(1, check(null, false, false, "a@example.org", "N", null, null).size());
    }

    @Test
    void theOutboxSendsNothingSoItIsNotAllowedInProduction() {
        assertEquals(1, check("outbox", false, true, "a@example.org", "N", null, null).size());
    }

    @Test
    void brevoNeedsAnApiKeyOfSomeLength() {
        assertEquals(1, check("brevo", false, true, "a@example.org", "N", null, null).size());
        assertEquals(1, check("brevo", false, true, "a@example.org", "N", null, "short").size());
        assertEquals(
                List.of(), check("brevo", false, true, "a@example.org", "N", null, "k".repeat(20)));
    }

    @Test
    void theSenderAddressAndNameMustBeValid() {
        for (String bad : new String[] {null, "", "nope", "a@b", "a@b.co\r\nBcc: x", "a b@c.de"}) {
            assertEquals(
                    1,
                    check("outbox", false, false, bad, "N", null, null).size(),
                    String.valueOf(bad));
        }
        assertEquals(1, check("outbox", false, false, "a@example.org", "", null, null).size());
        assertEquals(
                1,
                check("outbox", false, false, "a@example.org", "x".repeat(101), null, null).size());
        assertEquals(
                1, check("outbox", false, false, "a@example.org", "N\nBcc: x", null, null).size());
        assertEquals(
                List.of(),
                check("outbox", false, false, "a@example.org", "x".repeat(100), null, null));
        assertEquals(
                1,
                check("outbox", false, false, "a@example.org", "N", "not-an-address", null).size());
    }

    @Test
    void messagesNeverShowTheValues() {
        for (String problem :
                check("smtp", false, true, "secret-sender", "N", "secret-reply", "secret-key")) {
            assertFalse(problem.contains("secret-"));
        }
    }
}
