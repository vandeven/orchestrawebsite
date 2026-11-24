package org.example.website.platform.internal.startup;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.List;
import java.util.logging.Level;
import org.example.website.platform.api.PlatformConfig;
import org.jboss.logging.Logger;

/**
 * Checks the platform configuration when the application starts and refuses to start when it is
 * invalid (ARC-11, hosting: configuration at startup). It sets the log level (LGG-6) and logs the
 * result of the check, never the values (LGG-4).
 */
@ApplicationScoped
public class PlatformStartup {

    private static final Logger LOG = Logger.getLogger(PlatformStartup.class);

    @Inject PlatformConfig config;

    void onStart(@Observes StartupEvent event) {
        boolean production = LaunchMode.current() == LaunchMode.NORMAL;
        List<String> problems =
                ConfigRules.problems(
                        config.name(),
                        config.publicBaseUrl(),
                        config.logLevel(),
                        !production,
                        config.clientIpProxyHops());
        if (!ConfigRules.validImageOrigin(config.imageOrigin())) {
            problems = new java.util.ArrayList<>(problems);
            problems.add("IMAGES_BASE_URL must be an https:// address that ends with a slash");
        }
        if (!ConfigRules.validImageOrigin(config.fileOrigin())) {
            problems = new java.util.ArrayList<>(problems);
            problems.add("FILES_BASE_URL must be an https:// address that ends with a slash");
        }
        if (!problems.isEmpty()) {
            LOG.errorf("Configuration check failed: %s", String.join("; ", problems));
            throw new IllegalStateException(
                    "Invalid configuration, the application does not start: "
                            + String.join("; ", problems));
        }
        ConfigRules.LogLevel level = ConfigRules.resolveLogLevel(config.logLevel(), production);
        java.util.logging.Logger.getLogger("").setLevel(Level.parse(level.level()));
        if (level.downgraded()) {
            LOG.warn("LOG_LEVEL DEBUG is not accepted in production, using INFO");
        }
        LOG.infof("Configuration check passed (version %s)", config.version());
    }
}
