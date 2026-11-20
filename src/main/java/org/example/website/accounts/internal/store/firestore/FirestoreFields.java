package org.example.website.accounts.internal.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.example.website.platform.api.Database;

/** Reading and writing values of documents in the shape the stores use. */
public final class FirestoreFields {

    /** Every stored document says which version of the layout it has (ARC-5, HOS-3). */
    static final long SCHEMA_VERSION = 1;

    private static final int BATCH_SIZE = 400; // Firestore allows 500 writes in one batch

    private FirestoreFields() {}

    /**
     * Removes every document of the collection whose {@code userId} is the given user, in batches.
     * Repeats until nothing is left, so a document written in the meantime is removed too.
     */
    static void deleteWhereUser(Database database, CollectionReference collection, UUID userId) {
        while (true) {
            List<QueryDocumentSnapshot> found =
                    database.await(
                                    collection
                                            .whereEqualTo("userId", userId.toString())
                                            .limit(BATCH_SIZE)
                                            .get())
                            .getDocuments();
            if (found.isEmpty()) {
                return;
            }
            WriteBatch batch = database.firestore().batch();
            found.forEach(document -> batch.delete(document.getReference()));
            database.await(batch.commit());
        }
    }

    static Timestamp timestamp(Instant moment) {
        return moment == null
                ? null
                : Timestamp.ofTimeSecondsAndNanos(moment.getEpochSecond(), moment.getNano());
    }

    static Instant instant(DocumentSnapshot document, String field) {
        Timestamp value = document.getTimestamp(field);
        return value == null ? null : Instant.ofEpochSecond(value.getSeconds(), value.getNanos());
    }

    static UUID uuid(DocumentSnapshot document, String field) {
        String value = document.getString(field);
        return value == null ? null : UUID.fromString(value);
    }
}
