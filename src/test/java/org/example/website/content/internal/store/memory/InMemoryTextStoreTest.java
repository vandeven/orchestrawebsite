package org.example.website.content.internal.store.memory;

import org.example.website.content.internal.config.ContentConfig;
import org.example.website.content.internal.store.TextRepository;
import org.example.website.content.internal.store.TextStoreContract;

/** The in-memory store passes the same cases as the Firestore one (ARC-6). */
class InMemoryTextStoreTest extends TextStoreContract {

    private final InMemoryTexts store =
            new InMemoryTexts(
                    new ContentConfig() {
                        @Override
                        public String imageBaseUrl() {
                            return "https://images.example.test/";
                        }

                        @Override
                        public String imagesBucket() {
                            return "images";
                        }

                        @Override
                        public int uploadsPerHour() {
                            return 100;
                        }

                        @Override
                        public boolean allowInMemoryStorage() {
                            return true;
                        }
                    });

    @Override
    protected TextRepository texts() {
        return store;
    }

    @Override
    protected void reset() {
        // a new store for every case
    }
}
