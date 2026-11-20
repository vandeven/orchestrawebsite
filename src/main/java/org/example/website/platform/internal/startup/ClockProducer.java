package org.example.website.platform.internal.startup;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

/**
 * The clock of the application (ARC-6). Everything that needs the time asks for a {@link Clock}, so
 * tests can set the time, including the changes between summer and winter time. Moments are handled
 * in UTC and only converted to Europe/Amsterdam for display (plan: Language, dates and time zone).
 */
public class ClockProducer {

    @Produces
    @DefaultBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
