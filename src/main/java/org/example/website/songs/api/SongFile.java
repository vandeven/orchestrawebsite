package org.example.website.songs.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One file of a song. {@code originalName} is only for display: the place in storage is made by the
 * server and never comes from a name a person gave (MEM, HRD-4).
 */
public record SongFile(
        UUID id,
        UUID songId,
        FileType type,
        String displayName,
        String originalName,
        long sizeBytes,
        Instant uploadedAt) {}
