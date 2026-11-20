package org.example.website.platform.api.request;

import java.util.Optional;

/**
 * Checks the "return to this page after logging in" value (LOG-6). Only a path on this website is
 * accepted; anything else is ignored, so the login page can never send a visitor to another site
 * (no open redirect).
 */
public final class ReturnPath {

    /** The longest accepted path, including the query. */
    public static final int MAX_LENGTH = 2000;

    private ReturnPath() {}

    /** The value when it is a safe path on this website, otherwise nothing. */
    public static Optional<String> sanitize(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        // One slash to start, not two ("//host" is another site) and no backslash ("/\\host" is
        // read as "//host" by some browsers).
        if (value.charAt(0) != '/' || value.startsWith("//") || value.indexOf('\\') >= 0) {
            return Optional.empty();
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // control characters, line breaks, spaces and the percent sign (a second decoding
            // could hide a "//" or a backslash)
            if (c <= 0x20 || c == 0x7f || c == '%') {
                return Optional.empty();
            }
        }
        return Optional.of(value);
    }
}
