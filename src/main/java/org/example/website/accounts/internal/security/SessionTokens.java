package org.example.website.accounts.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Creates session tokens and the hashes that are stored instead of them (LOG-11). */
public final class SessionTokens {

    /** 256 bits from a cryptographically secure generator. */
    static final int TOKEN_BYTES = 32;

    /**
     * The length of a token as text (base64url without padding), so odd values are refused early.
     */
    static final int TOKEN_LENGTH = 43;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionTokens() {}

    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean looksValid(String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean ok =
                    (c >= 'a' && c <= 'z')
                            || (c >= 'A' && c <= 'Z')
                            || (c >= '0' && c <= '9')
                            || c == '-'
                            || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * The SHA-256 hash of the token as hex. A token has 256 bits of randomness, so no salt is
     * needed.
     */
    public static String hash(String token) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
