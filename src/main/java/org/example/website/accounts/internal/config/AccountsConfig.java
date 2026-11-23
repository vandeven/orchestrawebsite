package org.example.website.accounts.internal.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

/** Configuration of the accounts component (ARC-11). Checked when the application starts. */
@ConfigMapping(prefix = "accounts")
public interface AccountsConfig {

    /** The secret that is mixed into every password before hashing (LOG-18). Never logged. */
    String passwordPepper();

    /**
     * The master account's address. Only the master setup needs it (the job, or development), so
     * the website does not have it (USR-14).
     */
    Optional<String> masterEmail();

    /** The password of the master account (USR-14). Never logged, and not given to the website. */
    Optional<String> masterPassword();

    /** How many password hashes may run at the same time (LOG-23, HOS-24). */
    @WithDefault("4")
    int maxConcurrentHashes();

    /** Create or update the master account when the application starts (USR-15). */
    @WithDefault("false")
    boolean masterSetupOnStart();

    /** Allow the in-memory stores. Never switched on for a real deployment. */
    @WithDefault("false")
    boolean allowInMemoryStorage();

    /**
     * The wait after the first failed login of an account from one visitor; it doubles after that
     * (LOG-13).
     */
    @WithDefault("PT1S")
    Duration firstDelay();

    /** The longest wait of that kind. */
    @WithDefault("PT5M")
    Duration maxDelay();

    /** The counting period of the failure limits. The counters expire after it. */
    @WithDefault("PT1H")
    Duration window();

    /** Failed logins for one account from all visitors together before it is slowed down. */
    @WithDefault("100")
    int accountFailuresPerWindow();

    /** The time between attempts for a slowed down account. */
    @WithDefault("PT30S")
    Duration accountSlowdown();

    /** Failed logins from one visitor across all accounts before that visitor is refused. */
    @WithDefault("200")
    int visitorFailuresPerWindow();

    /**
     * A recovery request takes at least this long, so its time does not show whether an email was
     * sent.
     */
    @WithDefault("PT1.5S")
    Duration recoveryMinResponse();
}
