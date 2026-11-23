package org.example.website.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.example.website.platform.api.LegalConfig;
import org.example.website.site.web.LegalRules;
import org.junit.jupiter.api.Test;

class LegalRulesTest {

    private static LegalConfig config(String privacy, String address, String policy, int days) {
        return new LegalConfig() {
            public String foundationName() {
                return "Foundation";
            }

            public String address() {
                return address;
            }

            public String registrationNumber() {
                return "12345678";
            }

            public String contactEmail() {
                return "contact@example.org";
            }

            public String privacyEmail() {
                return privacy;
            }

            public String securityContact() {
                return "security@example.org";
            }

            public String securityPolicyUrl() {
                return policy;
            }

            public int logRetentionDays() {
                return days;
            }

            public int backupRetentionDays() {
                return 30;
            }

            public int deletedFilesDays() {
                return 7;
            }
        };
    }

    @Test
    void goodSettingsHaveNoProblems() {
        assertEquals(
                List.of(),
                LegalRules.problems(
                        config(
                                "privacy@example.org",
                                "Street 1, City",
                                "https://x.example/p",
                                30)));
    }

    @Test
    void everyWrongSettingIsNamedWithoutItsValue() {
        var problems =
                LegalRules.problems(config("not an address", "Line\nbreak", "http://x.example", 0));
        assertEquals(4, problems.size());
        String all = String.join(" ", problems);
        assertTrue(all.contains("LEGAL_PRIVACY_EMAIL"));
        assertTrue(all.contains("LEGAL_ADDRESS"));
        assertTrue(all.contains("security-policy-url"));
        assertTrue(all.contains("retention"));
        assertFalse(all.contains("not an address"));
    }

    @Test
    void anAddressOverTheLimitIsRefused() {
        assertEquals(
                1,
                LegalRules.problems(
                                config(
                                        "privacy@example.org",
                                        "a".repeat(301),
                                        "https://x.example",
                                        30))
                        .size());
    }
}
