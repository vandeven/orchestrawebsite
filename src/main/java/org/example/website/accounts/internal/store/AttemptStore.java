package org.example.website.accounts.internal.store;

import java.time.Duration;
import java.util.function.UnaryOperator;

/**
 * Where the login counters are kept (ARC-6, LOG-13). They are shared by all instances of the
 * application and expire by themselves. The keys are hashes: they never contain an email address or
 * an IP address.
 */
public interface AttemptStore {

    /** The counter, or an empty one when there is none or it has expired. */
    AttemptCounter get(String key);

    /**
     * Changes a counter in one atomic step (the Firestore store uses a transaction), and keeps it
     * for {@code ttl} from now. Returns the new value.
     */
    AttemptCounter update(String key, Duration ttl, UnaryOperator<AttemptCounter> change);

    /** Removes a counter, so the next failure starts again at one. */
    void reset(String key);
}
