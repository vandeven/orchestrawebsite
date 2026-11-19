package org.example.website.accounts.internal;

import java.util.ArrayList;
import java.util.List;
import org.example.website.accounts.api.AccountsRules;

/** The validation rules for the accounts configuration. Messages never contain the values. */
final class AccountsConfigRules {

    private AccountsConfigRules() {}

    static List<String> problems(
            String pepper, String masterEmail, String masterPassword, int maxConcurrentHashes) {
        List<String> problems = new ArrayList<>();
        if (pepper == null || pepper.length() < Argon2PasswordHasher.PEPPER_MIN_LENGTH) {
            problems.add(
                    "PASSWORD_PEPPER must be at least "
                            + Argon2PasswordHasher.PEPPER_MIN_LENGTH
                            + " characters");
        }
        if (!AccountsRules.validEmail(AccountsRules.normalizeEmail(masterEmail))) {
            problems.add(
                    "MASTER_EMAIL must be a valid email address of at most "
                            + AccountsRules.EMAIL_MAX
                            + " characters");
        }
        if (masterPassword == null
                || masterPassword.length() < AccountsRules.NEW_PASSWORD_MIN
                || masterPassword.length() > AccountsRules.PASSWORD_MAX) {
            problems.add(
                    "MASTER_PASSWORD must be %d to %d characters"
                            .formatted(AccountsRules.NEW_PASSWORD_MIN, AccountsRules.PASSWORD_MAX));
        }
        if (maxConcurrentHashes < 1 || maxConcurrentHashes > 32) {
            problems.add("accounts.max-concurrent-hashes must be from 1 to 32");
        }
        return problems;
    }
}
