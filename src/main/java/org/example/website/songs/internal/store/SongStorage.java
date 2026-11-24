package org.example.website.songs.internal.store;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.example.website.songs.internal.config.SongsConfig;
import org.example.website.songs.internal.store.firestore.FirestoreSongs;
import org.example.website.songs.internal.store.memory.InMemorySongs;

/**
 * Chooses where songs are kept (ARC-6): Firestore, or memory when {@code
 * songs.allow-in-memory-storage} is switched on (tests, development, the container smoke test).
 */
@ApplicationScoped
public class SongStorage {

    @Inject SongsConfig config;
    @Inject InMemorySongs memory;
    @Inject FirestoreSongs firestore;

    @Produces
    @ApplicationScoped
    SongRepository songs() {
        return config.allowInMemoryStorage() ? memory : firestore;
    }

    @Produces
    @ApplicationScoped
    SongFileRepository files() {
        return config.allowInMemoryStorage() ? memory : firestore;
    }
}
