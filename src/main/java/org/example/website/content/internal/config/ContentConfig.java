package org.example.website.content.internal.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Configuration of the content component (ARC-11). */
@ConfigMapping(prefix = "content")
public interface ContentConfig {

    /**
     * The address images in the texts must start with: the website's own image storage (EDT-14,
     * HOS-22). Images from anywhere else are removed when a text is saved and when it is shown.
     */
    String imageBaseUrl();

    /** Allow the in-memory store. Never switched on for a real deployment. */
    @WithDefault("false")
    boolean allowInMemoryStorage();
}
