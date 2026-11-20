package org.example.website.accounts.internal.recovery;

import java.time.Duration;

/**
 * Makes a request take at least a fixed time, so that the time of a recovery request does not show
 * whether an email was sent and therefore whether the address has an account (USR-18). The mail is
 * sent while the visitor waits: on Cloud Run, work after the response gets no processor time.
 */
final class Pacer {

    private Pacer() {}

    /** How much longer to wait, given how long the work took. Never negative. */
    static Duration remaining(Duration elapsed, Duration minimum) {
        Duration left = minimum.minus(elapsed);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** Waits until at least {@code minimum} has passed since {@code startNanos}. */
    static void waitUntil(long startNanos, Duration minimum) {
        Duration left = remaining(Duration.ofNanos(System.nanoTime() - startNanos), minimum);
        if (left.isZero()) {
            return;
        }
        try {
            Thread.sleep(left.toMillis(), (int) (left.toNanos() % 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
