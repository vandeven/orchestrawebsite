package org.example.website.accounts.api;

import java.util.Locale;

/**
 * The validation rules for accounts, defined once (ARC-9): the server checks them and the page
 * templates use the same numbers for the HTML attributes, so the browser and the server cannot
 * disagree.
 */
public final class AccountsRules {

    public static final int EMAIL_MAX = 254;
    public static final int PASSWORD_MAX = 128;
    public static final int NEW_PASSWORD_MIN = 12;

    private AccountsRules() {}

    /**
     * Leading and trailing spaces removed and lower case, so addresses compare without regard to
     * case.
     */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /** True when the (normalized) address has a valid format and is not too long. */
    public static boolean validEmail(String normalizedEmail) {
        return normalizedEmail != null
                && !normalizedEmail.isEmpty()
                && normalizedEmail.length() <= EMAIL_MAX
                && wellFormed(normalizedEmail);
    }

    /**
     * One @, something before and after it, a dot in the domain with something on both sides of it,
     * no spaces and no control characters. It is written as one pass over the characters and not as
     * a regular expression, because a pattern with a dot inside a repeated group can take quadratic
     * time on a crafted address (found by CodeQL).
     */
    private static boolean wellFormed(String email) {
        int at = -1;
        for (int i = 0; i < email.length(); i++) {
            char c = email.charAt(i);
            if (c <= ' ' || c == 0x7F) { // space, tab, line breaks and the other control characters
                return false;
            }
            if (c == '@') {
                if (at >= 0) {
                    return false; // a second @
                }
                at = i;
            }
        }
        if (at < 1) {
            return false; // no @, or nothing before it
        }
        // a dot in the domain with at least one character before it (after the @) and one after it
        for (int dot = at + 2; dot <= email.length() - 2; dot++) {
            if (email.charAt(dot) == '.') {
                return true;
            }
        }
        return false;
    }
}
