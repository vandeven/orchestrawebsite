package org.example.website.platform.internal.limits;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.example.website.platform.api.limits.RateLimiter;

/** Chooses where the counters are kept (ARC-6): Firestore, or memory for tests and development. */
@ApplicationScoped
public class RateLimiterChoice {

    @Inject LimitsConfig config;
    @Inject InMemoryRateLimiter memory;
    @Inject FirestoreRateLimiter firestore;

    @Produces
    @ApplicationScoped
    RateLimiter rateLimiter() {
        return config.allowInMemoryStorage() ? memory : firestore;
    }
}
