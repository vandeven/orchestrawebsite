package org.example.website.accounts.internal;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Configuration of the accounts component (ARC-11). Checked when the application starts. */
@ConfigMapping(prefix = "accounts")
interface AccountsConfig {

    /** The secret that is mixed into every password before hashing (LOG-18). Never logged. */
    String passwordPepper();

    String masterEmail();

    /** The password of the master account (USR-14). Never logged. */
    String masterPassword();

    /** How many password hashes may run at the same time (LOG-23, HOS-24). */
    @WithDefault("4")
    int maxConcurrentHashes();

    /** Create or update the master account when the application starts (USR-15). */
    @WithDefault("false")
    boolean masterSetupOnStart();

    /** Allow the in-memory stores. Never switched on for a real deployment. */
    @WithDefault("false")
    boolean allowInMemoryStorage();
}
