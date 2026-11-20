package org.example.website.accounts.internal.store.firestore;

import com.google.cloud.firestore.CollectionReference;
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
import java.util.function.UnaryOperator;
import org.example.website.accounts.internal.store.AttemptCounter;
import org.example.website.accounts.internal.store.AttemptStore;
import org.example.website.platform.api.database.Database;

/**
 * The login counters in Firestore (ARC-5, LOG-13). Collection {@code attempts}, one document per
 * counter, named by a hash (never an address). The counters are shared by every instance of the
 * application. A change is one transaction, so two logins that fail at the same moment on two
 * instances both count. {@code expiresAt} is the end of the counting period; an expired counter
 * counts as empty even when Firestore has not removed it yet.
 */
@ApplicationScoped
@Typed(FirestoreAttempts.class)
public class FirestoreAttempts implements AttemptStore {

    static final String ATTEMPTS = "attempts";

    /** Many failures at the same moment for one account must all count, so retry generously. */
    private static final TransactionOptions TRANSACTION =
            TransactionOptions.createReadWriteOptionsBuilder().setNumberOfAttempts(20).build();

    @Inject Database database;
    @Inject Clock clock;

    private CollectionReference attempts() {
        return database.firestore().collection(ATTEMPTS);
    }

    @Override
    public AttemptCounter get(String key) {
        DocumentSnapshot document = database.await(attempts().document(key).get());
        return current(document, clock.instant());
    }

    @Override
    public AttemptCounter update(String key, Duration ttl, UnaryOperator<AttemptCounter> change) {
        DocumentReference reference = attempts().document(key);
        return database.await(
                database.firestore()
                        .runTransaction(
                                transaction -> {
                                    Instant now = clock.instant();
                                    AttemptCounter changed =
                                            change.apply(
                                                    current(transaction.get(reference).get(), now));
                                    transaction.set(
                                            reference,
                                            Map.of(
                                                    "schemaVersion", FirestoreFields.SCHEMA_VERSION,
                                                    "failures", (long) changed.failures(),
                                                    "windowStart",
                                                            FirestoreFields.timestamp(
                                                                    changed.windowStart()),
                                                    "blockedUntil",
                                                            FirestoreFields.timestamp(
                                                                    changed.blockedUntil()),
                                                    "expiresAt",
                                                            FirestoreFields.timestamp(
                                                                    now.plus(ttl))));
                                    return changed;
                                },
                                TRANSACTION));
    }

    @Override
    public void reset(String key) {
        database.await(attempts().document(key).delete());
    }

    private static AttemptCounter current(DocumentSnapshot document, Instant now) {
        if (!document.exists()) {
            return AttemptCounter.EMPTY;
        }
        Instant expiresAt = FirestoreFields.instant(document, "expiresAt");
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return AttemptCounter.EMPTY;
        }
        Long failures = document.getLong("failures");
        return new AttemptCounter(
                failures == null ? 0 : Math.toIntExact(failures),
                FirestoreFields.instant(document, "windowStart"),
                FirestoreFields.instant(document, "blockedUntil"));
    }
}
