package org.example.website.accounts.internal.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** USR-18: a recovery request takes the same time whether or not an email was sent. */
class PacerTest {

    @Test
    void theRemainingTimeIsTheMinimumMinusTheTimeTheWorkTook() {
        assertEquals(
                Duration.ofMillis(1200),
                Pacer.remaining(Duration.ofMillis(300), Duration.ofMillis(1500)));
        assertEquals(
                Duration.ZERO, Pacer.remaining(Duration.ofMillis(1500), Duration.ofMillis(1500)));
        assertEquals(
                Duration.ZERO,
                Pacer.remaining(Duration.ofSeconds(9), Duration.ofMillis(1500)),
                "never negative");
        assertEquals(Duration.ZERO, Pacer.remaining(Duration.ZERO, Duration.ZERO));
    }

    @Test
    void aFastAndASlowRequestEndAtTheSameMoment() {
        Duration minimum = Duration.ofMillis(300);
        long fast = System.nanoTime();
        Pacer.waitUntil(fast, minimum); // no work at all
        long fastTook = (System.nanoTime() - fast) / 1_000_000;

        long slow = System.nanoTime();
        try {
            Thread.sleep(120); // some work, like sending a mail
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Pacer.waitUntil(slow, minimum);
        long slowTook = (System.nanoTime() - slow) / 1_000_000;

        assertTrue(
                fastTook >= 295 && slowTook >= 295,
                "both took at least the minimum: " + fastTook + " and " + slowTook);
        assertTrue(
                Math.abs(fastTook - slowTook) < 80,
                "and about the same: " + fastTook + " and " + slowTook);
    }

    @Test
    void zeroMinimumDoesNotWait() {
        long start = System.nanoTime();
        Pacer.waitUntil(start, Duration.ZERO);
        assertTrue((System.nanoTime() - start) / 1_000_000 < 50);
    }
}
