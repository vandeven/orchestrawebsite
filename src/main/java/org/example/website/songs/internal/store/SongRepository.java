package org.example.website.songs.internal.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.website.songs.api.Song;

/** Storage of songs (ARC-6). */
public interface SongRepository {

    void save(Song song);

    Optional<Song> find(UUID id);

    /** Every song, in no particular order. */
    List<Song> findAll();

    /** Not an error when the song does not exist. */
    void delete(UUID id);
}
