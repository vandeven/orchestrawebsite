package org.example.website.site.web;

import java.util.ArrayList;
import java.util.List;
import org.example.website.accounts.api.rules.AccountsRules;
import org.example.website.platform.api.LegalConfig;

/** The checks of the legal settings when the application starts. Messages never hold values. */
public final class LegalRules {

    private LegalRules() {}

    public static List<String> problems(LegalConfig config) {
        List<String> problems = new ArrayList<>();
        email(problems, "LEGAL_PRIVACY_EMAIL", config.privacyEmail());
        email(problems, "LEGAL_CONTACT_EMAIL", config.contactEmail());
        email(problems, "LEGAL_SECURITY_CONTACT", config.securityContact());
        text(problems, "LEGAL_FOUNDATION_NAME", config.foundationName(), 150);
        text(problems, "LEGAL_ADDRESS", config.address(), 300);
        text(problems, "LEGAL_REGISTRATION_NUMBER", config.registrationNumber(), 50);
        String policy = config.securityPolicyUrl();
        if (policy == null
                || !policy.startsWith("https://")
                || policy.chars().anyMatch(c -> Character.isISOControl(c) || c == ' ')) {
            problems.add("legal.security-policy-url must be an https:// address");
        }
        if (config.logRetentionDays() < 1
                || config.backupRetentionDays() < 1
                || config.deletedFilesDays() < 1) {
            problems.add("the retention periods must be at least 1 day");
        }
        return problems;
    }

    private static void email(List<String> problems, String name, String value) {
        String normalized = AccountsRules.normalizeEmail(value);
        if (value == null || !AccountsRules.validEmail(normalized)) {
            problems.add(name + " must be a valid email address of at most 254 characters");
        }
    }

    private static void text(List<String> problems, String name, String value, int max) {
        if (value == null
                || value.isBlank()
                || value.length() > max
                || value.chars().anyMatch(Character::isISOControl)) {
            problems.add(name + " must be 1 to " + max + " characters on one line");
        }
    }
}
