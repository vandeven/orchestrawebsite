package org.example.website.site.web;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.List;
import org.example.website.platform.api.LegalConfig;
import org.jboss.logging.Logger;

/** Refuses to start when the settings of the legal pages are wrong (ARC-11, LEG, input rules). */
@ApplicationScoped
class LegalStartup {

    private static final Logger LOG = Logger.getLogger(LegalStartup.class);

    @Inject LegalConfig config;

    void onStart(@Observes StartupEvent event) {
        List<String> problems = LegalRules.problems(config);
        if (!problems.isEmpty()) {
            LOG.errorf("Legal settings check failed: %s", String.join("; ", problems));
            throw new IllegalStateException(
                    "Invalid configuration, the application does not start: "
                            + String.join("; ", problems));
        }
        LOG.info("Legal settings check passed");
    }
}
