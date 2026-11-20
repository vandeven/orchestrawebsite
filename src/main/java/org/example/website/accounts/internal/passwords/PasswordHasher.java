package org.example.website.accounts.internal.passwords;

/** Hashes and checks passwords (ARC-6, LOG-16 to LOG-23). */
public interface PasswordHasher {

    /** A new hash string with its own random salt and the current settings. */
    String hash(String password);

    /**
     * True when the password belongs to the hash. The comparison takes the same time either way.
     */
    boolean verify(String password, String hash);

    /** True when the hash was made with older or weaker settings than the current ones (LOG-20). */
    boolean needsRehash(String hash);
}
