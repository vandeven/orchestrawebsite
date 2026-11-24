package org.example.website.platform.api;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * The details on the legal pages and in the footer (LEG-3, LEG-8) and the retention periods the
 * privacy page states (LEG-4). The details of the foundation come from the environment because they
 * are not in the public repository; the retention periods match the real settings and are checked
 * against them by tests. It lives in {@code platform} because every page, also the error pages, has
 * the footer.
 */
@ConfigMapping(prefix = "legal")
public interface LegalConfig {

    /** The legal name of the foundation. */
    String foundationName();

    /** Its postal address, on one line. */
    String address();

    /** The registration number, for example the Chamber of Commerce number. */
    String registrationNumber();

    /** The contact email address shown in the footer. */
    String contactEmail();

    /** Where privacy questions and requests go (LEG-3, LEG-5). */
    String privacyEmail();

    /** Where people report a security problem (HRD-11). */
    String securityContact();

    /** The policy people can read before they report a problem (HRD-11). */
    String securityPolicyUrl();

    /** Application logs and the audit trail are kept this many days (LGG-12). */
    @WithDefault("30")
    int logRetentionDays();

    /** Firestore backups are kept this many days (HOS-10). */
    @WithDefault("30")
    int backupRetentionDays();

    /** Deleted member files and songs are kept this many days before they are removed. */
    @WithDefault("7")
    int deletedFilesDays();
}
