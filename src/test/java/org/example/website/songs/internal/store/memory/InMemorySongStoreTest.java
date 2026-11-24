package org.example.website.songs.internal.store.memory;

import org.example.website.songs.internal.config.SongsConfig;
import org.example.website.songs.internal.store.SongFileRepository;
import org.example.website.songs.internal.store.SongRepository;
import org.example.website.songs.internal.store.SongStoreContract;

class InMemorySongStoreTest extends SongStoreContract {

    private final InMemorySongs store =
            new InMemorySongs(
                    new SongsConfig() {
                        @Override
                        public String filesBucket() {
                            return "files";
                        }

                        @Override
                        public int uploadsPerHour() {
                            return 200;
                        }

                        @Override
                        public boolean allowInMemoryStorage() {
                            return true;
                        }
                    });

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
        store.clear();
    }
}
