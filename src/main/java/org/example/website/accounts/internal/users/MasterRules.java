package org.example.website.accounts.internal.users;

import java.util.ArrayList;
import java.util.List;
import org.example.website.accounts.api.rules.AccountsRules;

/** The rules for the master account settings. Messages never contain the values. */
public final class MasterRules {

    private MasterRules() {}

    public static List<String> problems(String masterEmail, String masterPassword) {
        List<String> problems = new ArrayList<>();
        if (masterEmail == null
                || !AccountsRules.validEmail(AccountsRules.normalizeEmail(masterEmail))) {
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
        return problems;
    }
}
