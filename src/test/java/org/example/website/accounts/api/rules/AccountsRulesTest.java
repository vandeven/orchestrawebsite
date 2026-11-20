package org.example.website.accounts.api.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The address format check (USR-1, LOG-1): written without a regular expression on purpose. */
class AccountsRulesTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "a@b.c",
                "name.surname@example.org",
                "x+tag@sub.example.co.uk",
                "a@b.c.", // accepted as before: the dot has characters on both sides somewhere
                "é@ü.nl"
            })
    void ordinaryAddressesAreAccepted(String address) {
        assertTrue(AccountsRules.validEmail(address), address);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "plain",
                "@b.c",
                "a@",
                "a@b",
                "a@.c",
                "a@b.",
                "a@@b.c",
                "a@b@c.d",
                "a b@c.d",
                "a@b .c",
                "a\t@b.c",
                "a@b.c\n",
                "a\u0000@b.c",
                "a\u007f@b.c",
                "a.b.c"
            })
    void addressesWithoutTheRightShapeAreRefused(String address) {
        assertFalse(AccountsRules.validEmail(address), address);
    }

    @Test
    void nullAndTooLongAreRefused() {
        assertFalse(AccountsRules.validEmail(null));
        String local = "a".repeat(AccountsRules.EMAIL_MAX - 4);
        assertTrue(AccountsRules.validEmail(local + "@b.c"));
        assertFalse(AccountsRules.validEmail(local + "xx@b.c"));
    }

    @Test
    void aCraftedAddressIsCheckedInLinearTime() {
        // The pattern that CodeQL flagged took quadratic time on this shape of input
        String crafted = "!@!." + "!.".repeat(100_000);
        long start = System.nanoTime();
        assertFalse(AccountsRules.validEmail(crafted)); // too long: refused before any scan
        assertFalse(AccountsRules.validEmail(crafted.substring(0, AccountsRules.EMAIL_MAX + 1)));
        assertTrue(AccountsRules.validEmail(crafted.substring(0, AccountsRules.EMAIL_MAX)));
        assertTrue((System.nanoTime() - start) < 1_000_000_000L, "must not be slow");
    }

    @Test
    void normalizingStripsAndLowercases() {
        assertEquals("a@b.c", AccountsRules.normalizeEmail("  A@B.C "));
        assertEquals("", AccountsRules.normalizeEmail(null));
    }
}
