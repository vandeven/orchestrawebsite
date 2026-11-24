package org.example.website.content.internal.store;

import java.time.Instant;
import java.util.UUID;

/**
 * What is stored about one image. The file itself is in the image storage under {@code objectName}.
 */
public record ImageRecord(
        UUID id,
        String objectName,
        String contentType,
        int width,
        int height,
        long sizeBytes,
        String alt,
        Instant uploadedAt) {}
