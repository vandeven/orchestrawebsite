package org.example.website.platform.api.errors;

/** Expected problem (ARC-8): HTTP 429. The visitor has to wait before trying again. */
public class TooManyRequests extends ApplicationError {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public TooManyRequests(String logMessage, long retryAfterSeconds) {
        super(429, logMessage);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    /** How long the visitor should wait, in whole seconds, at least 1. */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
