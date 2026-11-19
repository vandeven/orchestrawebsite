package org.example.website.accounts.internal;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Checks the accounts configuration when the application starts and refuses to start when it is
 * invalid (ARC-11, LOG-19), then sets up the master account when that is switched on. The values
 * are never logged.
 */
@ApplicationScoped
class AccountsStartup {

    private static final Logger LOG = Logger.getLogger(AccountsStartup.class);

    @Inject AccountsConfig config;
    @Inject MasterAccountSetup masterSetup;

    void onStart(@Observes StartupEvent event) {
        List<String> problems =
                AccountsConfigRules.problems(
                        config.passwordPepper(),
                        config.masterEmail(),
                        config.masterPassword(),
                        config.maxConcurrentHashes());
        if (!problems.isEmpty()) {
            LOG.errorf("Accounts configuration check failed: %s", String.join("; ", problems));
            throw new IllegalStateException(
                    "Invalid configuration, the application does not start: "
                            + String.join("; ", problems));
        }
        LOG.info("Accounts configuration check passed");
        if (config.masterSetupOnStart()) {
            masterSetup.ensureMasterAccount();
        }
    }
}
