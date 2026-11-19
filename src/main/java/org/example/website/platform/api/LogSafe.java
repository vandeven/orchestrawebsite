package org.example.website.platform.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Makes values safe to write to the log (LGG-5, LGG-18). Email addresses of visitors who are not
 * logged in are never logged; a short hash is logged where an identifier is needed.
 */
public final class LogSafe {

    private LogSafe() {}

    /**
     * The first 12 hex characters of the SHA-256 hash of the value: enough to follow one address.
     */
    public static String hash(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
