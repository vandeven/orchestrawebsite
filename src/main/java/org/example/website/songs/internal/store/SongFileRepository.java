package org.example.website.songs.internal.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.website.songs.api.SongFile;

/** Storage of the details of song files (ARC-6). The files themselves are in the file storage. */
public interface SongFileRepository {

    void save(SongFile file);

    Optional<SongFile> findFile(UUID id);

    /** The files of a song, oldest upload first. */
    List<SongFile> findBySong(UUID songId);

    /** Not an error when the file does not exist. */
    void deleteFile(UUID id);
}
