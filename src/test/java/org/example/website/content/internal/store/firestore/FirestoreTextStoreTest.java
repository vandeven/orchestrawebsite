package org.example.website.content.internal.store.firestore;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.example.website.content.api.texts.TextKey;
import org.example.website.content.internal.store.TextRepository;
import org.example.website.content.internal.store.TextStoreContract;
import org.example.website.platform.api.database.Database;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The Firestore text store against the emulator, with the same cases as the in-memory one. */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestoreTextStoreTest extends TextStoreContract {

    @Inject Database database;
    @Inject FirestoreTexts texts;

    @Override
    protected TextRepository texts() {
        return texts;
    }

    @Override
    protected void reset() {
        for (TextKey key : TextKey.values()) {
            database.await(
                    database.firestore()
                            .collection(FirestoreTexts.TEXTS)
                            .document(key.key())
                            .delete());
        }
    }
}
