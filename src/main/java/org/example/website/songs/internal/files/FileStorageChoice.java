package org.example.website.songs.internal.files;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.example.website.songs.internal.config.SongsConfig;

/** Chooses the file storage (ARC-6): Cloud Storage, or memory for tests and development. */
@ApplicationScoped
public class FileStorageChoice {

    @Inject SongsConfig config;
    @Inject InMemoryFileStorage memory;
    @Inject GcsFileStorage gcs;

    @Produces
    @ApplicationScoped
    FileStorage fileStorage() {
        return config.allowInMemoryStorage() ? memory : gcs;
    }
}
