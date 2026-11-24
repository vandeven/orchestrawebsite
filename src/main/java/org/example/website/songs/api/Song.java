package org.example.website.songs.api;

import java.time.Instant;
import java.util.UUID;

/**
 * A song the orchestra works on (MEM). An archived song is hidden from members but kept with its
 * files (MEM-12). {@code notes} is plain text, never HTML.
 */
public record Song(
        UUID id,
        String title,
        String notes,
        boolean archived,
        Instant createdAt,
        Instant statusChangedAt,
        int displayOrder) {}
