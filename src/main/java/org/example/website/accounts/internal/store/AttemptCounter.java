package org.example.website.accounts.internal.store;

import java.time.Instant;

/**
 * A counter of failed logins (LOG-13). {@code windowStart} is when the counting period began and
 * {@code blockedUntil} is the moment until which attempts are refused.
 */
public record AttemptCounter(int failures, Instant windowStart, Instant blockedUntil) {

    public static final AttemptCounter EMPTY = new AttemptCounter(0, Instant.EPOCH, Instant.EPOCH);
}
