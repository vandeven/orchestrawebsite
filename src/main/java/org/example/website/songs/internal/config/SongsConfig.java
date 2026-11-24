package org.example.website.songs.internal.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Configuration of the songs component (ARC-11). */
@ConfigMapping(prefix = "songs")
public interface SongsConfig {

    /** The name of the private files bucket in Cloud Storage (HOS-11). */
    String filesBucket();

    /** How many upload addresses one admin may ask for per hour (HRD-7). */
    @WithDefault("200")
    int uploadsPerHour();

    /** Allow the in-memory stores. Never switched on for a real deployment. */
    @WithDefault("false")
    boolean allowInMemoryStorage();
}
