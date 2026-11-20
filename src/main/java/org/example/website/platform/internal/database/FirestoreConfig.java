package org.example.website.platform.internal.database;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

/** Configuration of the database connection (ARC-11). */
@ConfigMapping(prefix = "firestore")
interface FirestoreConfig {

    /**
     * The Google Cloud project. Empty on Cloud Run and in other Google environments, where it is
     * found automatically. The emulator needs one.
     */
    Optional<String> projectId();

    @WithDefault("(default)")
    String databaseId();

    /** How long one database call may take before it counts as unavailable (HOS-24). */
    @WithDefault("PT10S")
    Duration timeout();
}
