package org.example.website.accounts.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Rate limiting of failed logins that works on shared networks (LOG-13). Members often log in from
 * the same WiFi and share one IP address, so the limits are mostly about the account, and nobody
 * can lock a member out on purpose. Only failures count; a right password never makes things worse.
 * There are three limits:
 *
 * <ul>
 *   <li><b>One account from one visitor:</b> every failure doubles the wait (1, 2, 4, 8 seconds, up
 *       to 5 minutes). It is a wait, not a lock, and it only concerns that one visitor.
 *   <li><b>One account from everybody:</b> after 100 failures in an hour the account is slowed down
 *       for everyone to one attempt every 30 seconds, and the owner is alerted. The account's user
 *       can still use the password recovery.
 *   <li><b>One visitor across all accounts:</b> after 200 failures in an hour that visitor is
 *       refused until the hour is over, which stops password spraying without blocking a network
 *       full of members who all type the right password.
 * </ul>
 *
 * The same counters exist for addresses that have no account, so the answer never shows which
 * accounts exist. All checks are cheap and happen before any password is hashed.
 */
@ApplicationScoped
class LoginThrottle {

    private static final Logger LOG = Logger.getLogger(LoginThrottle.class);

    @Inject AttemptStore store;
    @Inject Fingerprints fingerprints;
    @Inject AccountsConfig config;
    @Inject Clock clock;

    /**
     * How long this attempt must wait, or nothing when it may go ahead.
     *
     * @param visitorAddress the visitor's address, or null when it is not known (then only the
     *     limit for the account in total applies)
     */
    Optional<Duration> waitTime(String normalizedEmail, String visitorAddress) {
        Instant now = clock.instant();
        String account = fingerprints.email(normalizedEmail);
        Duration longest = Duration.ZERO;

        if (visitorAddress != null) {
            String visitor = fingerprints.address(visitorAddress);
            longest =
                    longer(
                            longest,
                            remaining(
                                    store.get(accountVisitorKey(account, visitor)).blockedUntil(),
                                    now));
            AttemptCounter spraying = store.get(visitorKey(visitor));
            if (spraying.failures() >= config.visitorFailuresPerWindow()) {
                longest =
                        longer(
                                longest,
                                remaining(spraying.windowStart().plus(config.window()), now));
            }
        }
        AttemptCounter total = store.get(accountKey(account));
        if (total.failures() >= config.accountFailuresPerWindow()) {
            longest = longer(longest, remaining(total.blockedUntil(), now));
        }
        return longest.isZero() ? Optional.empty() : Optional.of(longest);
    }

    void recordFailure(String normalizedEmail, String visitorAddress) {
        Instant now = clock.instant();
        String account = fingerprints.email(normalizedEmail);

        AttemptCounter total =
                store.update(
                        accountKey(account),
                        config.window(),
                        counter -> {
                            AttemptCounter current = inWindow(counter, now);
                            int failures = current.failures() + 1;
                            Instant blocked =
                                    failures >= config.accountFailuresPerWindow()
                                            ? now.plus(config.accountSlowdown())
                                            : current.blockedUntil();
                            return new AttemptCounter(failures, current.windowStart(), blocked);
                        });
        if (total.failures() == config.accountFailuresPerWindow()) {
            // The owner is alerted through the log (LGG-4, LOG-13). No address is in the message.
            LOG.errorf(
                    "ALERT: one account had %d failed logins within %s and is slowed down for everyone",
                    total.failures(), config.window());
        }

        if (visitorAddress == null) {
            return;
        }
        String visitor = fingerprints.address(visitorAddress);
        store.update(
                accountVisitorKey(account, visitor),
                config.window(),
                counter -> {
                    int failures = inWindow(counter, now).failures() + 1;
                    return new AttemptCounter(
                            failures,
                            inWindow(counter, now).windowStart(),
                            now.plus(delayFor(failures)));
                });
        AttemptCounter spraying =
                store.update(
                        visitorKey(visitor),
                        config.window(),
                        counter -> {
                            AttemptCounter current = inWindow(counter, now);
                            return new AttemptCounter(
                                    current.failures() + 1,
                                    current.windowStart(),
                                    current.blockedUntil());
                        });
        if (spraying.failures() == config.visitorFailuresPerWindow()) {
            LOG.warnf(
                    "One visitor had %d failed logins within %s and is refused for the rest of that time",
                    spraying.failures(), config.window());
        }
    }

    /** A right password ends the waiting for that account and visitor. Other counters stay. */
    void recordSuccess(String normalizedEmail, String visitorAddress) {
        if (visitorAddress != null) {
            store.reset(
                    accountVisitorKey(
                            fingerprints.email(normalizedEmail),
                            fingerprints.address(visitorAddress)));
        }
    }

    /** 1 second after the first failure, then 2, 4, 8 and so on, never more than the maximum. */
    Duration delayFor(int failures) {
        long seconds = config.firstDelay().toSeconds();
        for (int i = 1; i < failures && seconds < config.maxDelay().toSeconds(); i++) {
            seconds *= 2;
        }
        return Duration.ofSeconds(Math.min(seconds, config.maxDelay().toSeconds()));
    }

    /** The counter as it is now, or a new one when its counting period is over. */
    private AttemptCounter inWindow(AttemptCounter counter, Instant now) {
        if (counter.failures() == 0 || !now.isBefore(counter.windowStart().plus(config.window()))) {
            return new AttemptCounter(0, now, Instant.EPOCH);
        }
        return counter;
    }

    private static Duration remaining(Instant until, Instant now) {
        return until.isAfter(now) ? Duration.between(now, until) : Duration.ZERO;
    }

    private static Duration longer(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String accountKey(String account) {
        return "login-account:" + account;
    }

    private static String visitorKey(String visitor) {
        return "login-visitor:" + visitor;
    }

    private static String accountVisitorKey(String account, String visitor) {
        return "login-account-visitor:" + account + ":" + visitor;
    }
}
