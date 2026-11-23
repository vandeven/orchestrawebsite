package org.example.website.accounts.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** LOG-19 and the configuration of the master account: valid, boundary and invalid input. */
class AccountsConfigRulesTest {

    private static final String PEPPER = "p".repeat(32);

    @Test
    void validConfigurationHasNoProblems() {
        assertEquals(
                List.of(),
                AccountsConfigRules.problems(PEPPER, "master@example.org", "a".repeat(12), 4));
    }

    @Test
    void pepperLengthBoundary() {
        assertEquals(
                List.of(),
                AccountsConfigRules.problems("p".repeat(32), "a@b.co", "x".repeat(12), 4));
        assertEquals(
                1,
                AccountsConfigRules.problems("p".repeat(31), "a@b.co", "x".repeat(12), 4).size());
        assertEquals(1, AccountsConfigRules.problems("", "a@b.co", "x".repeat(12), 4).size());
        assertEquals(1, AccountsConfigRules.problems(null, "a@b.co", "x".repeat(12), 4).size());
    }

    @Test
    void masterPasswordLengthBoundary() {
        assertEquals(1, AccountsConfigRules.problems(PEPPER, "a@b.co", "x".repeat(11), 4).size());
        assertEquals(List.of(), AccountsConfigRules.problems(PEPPER, "a@b.co", "x".repeat(12), 4));
        assertEquals(List.of(), AccountsConfigRules.problems(PEPPER, "a@b.co", "x".repeat(128), 4));
        assertEquals(1, AccountsConfigRules.problems(PEPPER, "a@b.co", "x".repeat(129), 4).size());
        assertEquals(1, AccountsConfigRules.problems(PEPPER, "a@b.co", null, 4).size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"no-at-sign", "a@b", "@b.co", "a@@b.co", "a b@c.co"})
    void masterEmailMustBeValid(String email) {
        assertEquals(1, AccountsConfigRules.problems(PEPPER, email, "x".repeat(12), 4).size());
    }

    @Test
    void surroundingWhitespaceOfTheMasterEmailIsIgnored() {
        // An environment variable often ends with a line break; addresses are stripped (see login)
        assertEquals(
                List.of(), AccountsConfigRules.problems(PEPPER, " a@b.co\n", "x".repeat(12), 4));
    }

    @Test
    void hashingLimitBoundary() {
        assertEquals(1, AccountsConfigRules.problems(PEPPER, "a@b.co", "x".repeat(12), 0).size());
        assertEquals(List.of(), AccountsConfigRules.problems(PEPPER, "a@b.co", "x".repeat(12), 1));
        assertEquals(List.of(), AccountsConfigRules.problems(PEPPER, "a@b.co", "x".repeat(12), 32));
        assertEquals(1, AccountsConfigRules.problems(PEPPER, "a@b.co", "x".repeat(12), 33).size());
    }

    @Test
    void messagesNeverShowTheValues() {
        var problems =
                AccountsConfigRules.problems(
                        "short-secret-pepper", "not-an-email-secret", "tiny", 4);
        assertEquals(3, problems.size());
        for (String problem : problems) {
            assertFalse(problem.contains("short-secret-pepper"));
            assertFalse(problem.contains("not-an-email-secret"));
            assertFalse(problem.contains("tiny"));
        }
    }

    @Test
    void theWebsiteDoesNotNeedTheMasterSettings() {
        assertEquals(List.of(), AccountsConfigRules.webProblems(PEPPER, 4));
        assertEquals(1, AccountsConfigRules.webProblems("short", 4).size());
        assertEquals(
                2, AccountsConfigRules.masterProblems(null, null).size(), "the setup needs both");
        assertEquals(List.of(), AccountsConfigRules.masterProblems("a@b.co", "x".repeat(12)));
    }
}
