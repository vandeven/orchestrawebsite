package org.example.website.accounts.api;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The validation rules for accounts, defined once (ARC-9): the server checks them and the page
 * templates use the same numbers for the HTML attributes, so the browser and the server cannot
 * disagree.
 */
public final class AccountsRules {

    public static final int EMAIL_MAX = 254;
    public static final int PASSWORD_MAX = 128;
    public static final int NEW_PASSWORD_MIN = 12;

    /**
     * One @, something before and after it, a dot in the domain, no spaces or control characters.
     */
    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s\\p{Cntrl}]+@[^@\\s\\p{Cntrl}]+\\.[^@\\s\\p{Cntrl}]+$");

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
                && EMAIL.matcher(normalizedEmail).matches();
    }
}
