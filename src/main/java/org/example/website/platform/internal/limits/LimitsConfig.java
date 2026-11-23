package org.example.website.platform.internal.limits;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Configuration of the shared request counters. */
@ConfigMapping(prefix = "limits")
public interface LimitsConfig {

    /** Count in memory instead of Firestore. Never switched on for a real deployment. */
    @WithDefault("false")
    boolean allowInMemoryStorage();
}
