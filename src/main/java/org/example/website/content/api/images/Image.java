package org.example.website.content.api.images;

import java.time.Instant;
import java.util.UUID;

/**
 * One image of the library. {@code url} is the address visitors load it from: the public images
 * bucket, never the application (HOS-22). {@code alt} is the default description (EDT-12); it is
 * empty for an image that is only decorative.
 */
public record Image(
        UUID id,
        String url,
        String contentType,
        int width,
        int height,
        long sizeBytes,
        String alt,
        Instant uploadedAt) {}
