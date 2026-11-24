package org.example.website.content.internal.images;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.example.website.content.internal.config.ContentConfig;

/** Chooses the image storage (ARC-6): Cloud Storage, or memory for tests and development. */
@ApplicationScoped
public class ImageStorageChoice {

    @Inject ContentConfig config;
    @Inject InMemoryImageStorage memory;
    @Inject GcsImageStorage gcs;

    @Produces
    @ApplicationScoped
    ImageStorage imageStorage() {
        return config.allowInMemoryStorage() ? memory : gcs;
    }
}
