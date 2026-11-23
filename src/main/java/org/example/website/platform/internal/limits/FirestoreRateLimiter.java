package org.example.website.platform.internal.limits;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.TransactionOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.example.website.platform.api.database.Database;
import org.example.website.platform.api.limits.RateLimiter;

/**
 * The counters in Firestore (ARC-5). Collection {@code rateLimits}, one document per key, named by
 * the key (a hash, never an address). A count is one transaction, so two requests at the same
 * moment on two instances both count. {@code expiresAt} is the end of the period: Firestore removes
 * the document by itself some time later, and an expired document counts as empty before that.
 */
@ApplicationScoped
@Typed(FirestoreRateLimiter.class)
public class FirestoreRateLimiter implements RateLimiter {

    static final String COLLECTION = "rateLimits";
    private static final long SCHEMA_VERSION = 1;
    private static final TransactionOptions TRANSACTION =
            TransactionOptions.createReadWriteOptionsBuilder().setNumberOfAttempts(20).build();

    @Inject Database database;
    @Inject Clock clock;

    @Override
    public Hit hit(String key, Duration period) {
        DocumentReference reference = database.firestore().collection(COLLECTION).document(key);
        Instant now = clock.instant();
        return database.await(
                database.firestore()
                        .runTransaction(
                                transaction -> {
                                    DocumentSnapshot document = transaction.get(reference).get();
                                    int count = 1;
                                    Instant start = now;
                                    if (document.exists()) {
                                        Timestamp expires = document.getTimestamp("expiresAt");
                                        Timestamp started = document.getTimestamp("windowStart");
                                        Long before = document.getLong("count");
                                        if (expires != null
                                                && started != null
                                                && before != null
                                                && now.isBefore(instant(expires))) {
                                            count = Math.toIntExact(before) + 1;
                                            start = instant(started);
                                        }
                                    }
                                    Instant end = start.plus(period);
                                    transaction.set(
                                            reference,
                                            Map.of(
                                                    "schemaVersion",
                                                    SCHEMA_VERSION,
                                                    "count",
                                                    (long) count,
                                                    "windowStart",
                                                    timestamp(start),
                                                    "expiresAt",
                                                    timestamp(end)));
                                    return new Hit(count, Duration.between(now, end));
                                },
                                TRANSACTION));
    }

    private static Instant instant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }
}
