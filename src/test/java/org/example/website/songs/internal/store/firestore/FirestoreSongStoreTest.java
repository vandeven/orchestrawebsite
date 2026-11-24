package org.example.website.songs.internal.store.firestore;

import com.google.cloud.firestore.QueryDocumentSnapshot;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.example.website.platform.api.database.Database;
import org.example.website.songs.internal.store.SongFileRepository;
import org.example.website.songs.internal.store.SongRepository;
import org.example.website.songs.internal.store.SongStoreContract;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The Firestore song stores against the emulator, with the same cases as the in-memory ones. */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class FirestoreSongStoreTest extends SongStoreContract {

    @Inject Database database;
    @Inject FirestoreSongs store;

    @Override
    protected SongRepository songs() {
        return store;
    }

    @Override
    protected SongFileRepository files() {
        return store;
    }

    @Override
    protected void reset() {
        for (String collection : new String[] {FirestoreSongs.SONGS, FirestoreSongs.FILES}) {
            for (QueryDocumentSnapshot document :
                    database.await(database.firestore().collection(collection).get())
                            .getDocuments()) {
                database.await(document.getReference().delete());
            }
        }
    }
}
