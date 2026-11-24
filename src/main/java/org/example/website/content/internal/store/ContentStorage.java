package org.example.website.content.internal.store;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.example.website.content.internal.config.ContentConfig;
import org.example.website.content.internal.store.firestore.FirestoreImages;
import org.example.website.content.internal.store.firestore.FirestoreTexts;
import org.example.website.content.internal.store.memory.InMemoryImages;
import org.example.website.content.internal.store.memory.InMemoryTexts;

/**
 * Chooses where the texts are kept (ARC-6): Firestore, or memory when {@code
 * content.allow-in-memory-storage} is switched on (tests, development, the container smoke test).
 */
@ApplicationScoped
public class ContentStorage {

    @Inject ContentConfig config;
    @Inject InMemoryTexts memory;
    @Inject FirestoreTexts firestore;
    @Inject InMemoryImages memoryImages;
    @Inject FirestoreImages firestoreImages;

    @Produces
    @ApplicationScoped
    TextRepository texts() {
        return config.allowInMemoryStorage() ? memory : firestore;
    }

    @Produces
    @ApplicationScoped
    ImageRepository images() {
        return config.allowInMemoryStorage() ? memoryImages : firestoreImages;
    }
}
