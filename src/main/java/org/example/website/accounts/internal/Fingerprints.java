package org.example.website.accounts.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Turns email addresses and IP addresses into keys for the login counters. They are keyed hashes
 * (HMAC-SHA-256 with the pepper), so a copy of the counters cannot be turned back into addresses,
 * not even by trying all IPv4 addresses (LGG-5).
 */
@ApplicationScoped
class Fingerprints {

    private final byte[] key;

    @Inject
    Fingerprints(AccountsConfig config) {
        this(config.passwordPepper());
    }

    Fingerprints(String pepper) {
        this.key = pepper.getBytes(StandardCharsets.UTF_8);
    }

    String email(String normalizedEmail) {
        return hash("email:" + cut(normalizedEmail));
    }

    String address(String clientAddress) {
        return hash("address:" + cut(clientAddress));
    }

    /** A bound on the input, so a huge value cannot be used to make the hashing slow. */
    private static String cut(String value) {
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    private String hash(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of()
                    .formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)), 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }
}
