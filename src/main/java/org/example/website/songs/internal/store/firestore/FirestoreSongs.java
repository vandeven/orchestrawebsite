package org.example.website.songs.internal.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.example.website.platform.api.database.Database;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.Song;
import org.example.website.songs.api.SongFile;
import org.example.website.songs.internal.store.SongFileRepository;
import org.example.website.songs.internal.store.SongRepository;

/**
 * Songs and the details of their files in Firestore (ARC-5). Collections {@code songs} and {@code
 * songFiles}, one document per song or file, named by the ID. The list of songs is small (some
 * hundreds at most), so it is read whole.
 */
@ApplicationScoped
@Typed(FirestoreSongs.class)
public class FirestoreSongs implements SongRepository, SongFileRepository {

    static final String SONGS = "songs";
    static final String FILES = "songFiles";
    private static final long SCHEMA_VERSION = 1;

    @Inject Database database;

    // Songs

    @Override
    public void save(Song song) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("schemaVersion", SCHEMA_VERSION);
        fields.put("title", song.title());
        fields.put("notes", song.notes());
        fields.put("archived", song.archived());
        fields.put("createdAt", timestamp(song.createdAt()));
        fields.put("statusChangedAt", timestamp(song.statusChangedAt()));
        fields.put("displayOrder", (long) song.displayOrder());
        database.await(
                database.firestore().collection(SONGS).document(song.id().toString()).set(fields));
    }

    @Override
    public Optional<Song> find(UUID id) {
        DocumentSnapshot document =
                database.await(
                        database.firestore().collection(SONGS).document(id.toString()).get());
        return document.exists() ? Optional.of(song(document)) : Optional.empty();
    }

    @Override
    public List<Song> findAll() {
        return database.await(database.firestore().collection(SONGS).get()).getDocuments().stream()
                .map(FirestoreSongs::song)
                .toList();
    }

    @Override
    public void delete(UUID id) {
        database.await(database.firestore().collection(SONGS).document(id.toString()).delete());
    }

    // Files

    @Override
    public void save(SongFile file) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("schemaVersion", SCHEMA_VERSION);
        fields.put("songId", file.songId().toString());
        fields.put("type", file.type().code());
        fields.put("displayName", file.displayName());
        fields.put("originalName", file.originalName());
        fields.put("sizeBytes", file.sizeBytes());
        fields.put("uploadedAt", timestamp(file.uploadedAt()));
        database.await(
                database.firestore().collection(FILES).document(file.id().toString()).set(fields));
    }

    @Override
    public Optional<SongFile> findFile(UUID id) {
        DocumentSnapshot document =
                database.await(
                        database.firestore().collection(FILES).document(id.toString()).get());
        return document.exists() ? Optional.of(file(document)) : Optional.empty();
    }

    @Override
    public List<SongFile> findBySong(UUID songId) {
        return database
                .await(
                        database.firestore()
                                .collection(FILES)
                                .whereEqualTo("songId", songId.toString())
                                .get())
                .getDocuments()
                .stream()
                .map(FirestoreSongs::file)
                .sorted(java.util.Comparator.comparing(SongFile::uploadedAt))
                .toList();
    }

    @Override
    public void deleteFile(UUID id) {
        database.await(database.firestore().collection(FILES).document(id.toString()).delete());
    }

    // Conversions

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    private static Instant instant(DocumentSnapshot document, String field) {
        Timestamp value = document.getTimestamp(field);
        return Instant.ofEpochSecond(value.getSeconds(), value.getNanos());
    }

    private static Song song(DocumentSnapshot document) {
        return new Song(
                UUID.fromString(document.getId()),
                document.getString("title"),
                document.getString("notes"),
                Boolean.TRUE.equals(document.getBoolean("archived")),
                instant(document, "createdAt"),
                instant(document, "statusChangedAt"),
                Math.toIntExact(document.getLong("displayOrder")));
    }

    private static SongFile file(DocumentSnapshot document) {
        return new SongFile(
                UUID.fromString(document.getId()),
                UUID.fromString(document.getString("songId")),
                FileType.fromCode(document.getString("type")).orElseThrow(),
                document.getString("displayName"),
                document.getString("originalName"),
                document.getLong("sizeBytes"),
                instant(document, "uploadedAt"));
    }
}
