package org.example.website.platform.api.limits;

import java.time.Duration;

/**
 * Counts requests per key in a fixed period (LOG-13, BAM-16). The counters are shared by all
 * instances of the application and expire by themselves. Keys must never contain an email address
 * or an IP address: use a hash.
 */
public interface RateLimiter {

    /**
     * Counts one more request for the key and returns how many there have been in the current
     * period. The period starts with the first request and is not extended by later ones.
     */
    Hit hit(String key, Duration period);

    /** The count so far (this request included) and how long until the period ends. */
    record Hit(int count, Duration untilReset) {}
}
