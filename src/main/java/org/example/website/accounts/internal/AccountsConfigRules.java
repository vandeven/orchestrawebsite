package org.example.website.accounts.internal;

import java.util.ArrayList;
import java.util.List;
import org.example.website.accounts.internal.passwords.Argon2PasswordHasher;
import org.example.website.accounts.internal.users.MasterRules;

/** The validation rules for the accounts configuration. Messages never contain the values. */
public final class AccountsConfigRules {

    private AccountsConfigRules() {}

    /** Every setting: what the website needs, and what the master setup needs. */
    static List<String> problems(
            String pepper, String masterEmail, String masterPassword, int maxConcurrentHashes) {
        List<String> problems = new ArrayList<>(webProblems(pepper, maxConcurrentHashes));
        problems.addAll(masterProblems(masterEmail, masterPassword));
        return problems;
    }

    /** What the website needs. The master settings are not part of it (USR-14). */
    static List<String> webProblems(String pepper, int maxConcurrentHashes) {
        List<String> problems = new ArrayList<>();
        if (pepper == null || pepper.length() < Argon2PasswordHasher.PEPPER_MIN_LENGTH) {
            problems.add(
                    "PASSWORD_PEPPER must be at least "
                            + Argon2PasswordHasher.PEPPER_MIN_LENGTH
                            + " characters");
        }
        if (maxConcurrentHashes < 1 || maxConcurrentHashes > 32) {
            problems.add("accounts.max-concurrent-hashes must be from 1 to 32");
        }
        return problems;
    }

    /** What the master account setup needs, checked where it runs. */
    public static List<String> masterProblems(String masterEmail, String masterPassword) {
        return MasterRules.problems(masterEmail, masterPassword);
    }
}
