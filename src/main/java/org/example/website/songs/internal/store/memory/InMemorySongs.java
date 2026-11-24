package org.example.website.songs.internal.store.memory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.example.website.songs.api.Song;
import org.example.website.songs.api.SongFile;
import org.example.website.songs.internal.config.SongsConfig;
import org.example.website.songs.internal.store.SongFileRepository;
import org.example.website.songs.internal.store.SongRepository;
import org.jboss.logging.Logger;

/** Songs and file details in memory, for tests and local work. Lost when the application stops. */
@ApplicationScoped
@Typed(InMemorySongs.class)
public class InMemorySongs implements SongRepository, SongFileRepository {

    private static final Logger LOG = Logger.getLogger(InMemorySongs.class);

    private final Map<UUID, Song> songs = new ConcurrentHashMap<>();
    private final Map<UUID, SongFile> files = new ConcurrentHashMap<>();
    private final SongsConfig config;

    @Inject
    public InMemorySongs(SongsConfig config) {
        this.config = config;
    }

    private void checkAllowed() {
        if (!config.allowInMemoryStorage()) {
            LOG.error("No database is configured for songs and in-memory storage is not allowed");
            throw new ServiceUnavailable("No song storage configured");
        }
    }

    @Override
    public void save(Song song) {
        checkAllowed();
        songs.put(song.id(), song);
    }

    @Override
    public Optional<Song> find(UUID id) {
        checkAllowed();
        return Optional.ofNullable(songs.get(id));
    }

    @Override
    public List<Song> findAll() {
        checkAllowed();
        return new ArrayList<>(songs.values());
    }

    @Override
    public void delete(UUID id) {
        checkAllowed();
        songs.remove(id);
    }

    @Override
    public void save(SongFile file) {
        checkAllowed();
        files.put(file.id(), file);
    }

    @Override
    public Optional<SongFile> findFile(UUID id) {
        checkAllowed();
        return Optional.ofNullable(files.get(id));
    }

    @Override
    public List<SongFile> findBySong(UUID songId) {
        checkAllowed();
        return files.values().stream()
                .filter(file -> file.songId().equals(songId))
                .sorted(Comparator.comparing(SongFile::uploadedAt))
                .toList();
    }

    @Override
    public void deleteFile(UUID id) {
        checkAllowed();
        files.remove(id);
    }

    /** For tests. */
    public void clear() {
        songs.clear();
        files.clear();
    }
}
