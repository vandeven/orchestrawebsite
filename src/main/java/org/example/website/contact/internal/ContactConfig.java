package org.example.website.contact.internal;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Configuration of the contact component (ARC-11). */
@ConfigMapping(prefix = "contact")
public interface ContactConfig {

    /** How many messages the whole website accepts a day (BAM-16, HRD-7). */
    @WithDefault("50")
    int dailyLimit();

    /** How many messages one visitor may send per hour (BAM-16). */
    @WithDefault("5")
    int perVisitorPerHour();
}
