package org.example.website.accounts.api.rules;

import java.util.Optional;

/**
 * The rules for a new password (USR-20, LOG-22): at least 12 and at most 128 characters, and not on
 * the list of very common passwords. The rules are only checked when a password is set, never when
 * logging in.
 */
public interface PasswordPolicy {

    /**
     * What is wrong with the password, written for the person who typed it, or nothing when it is
     * fine.
     */
    Optional<String> problem(String password);
}
