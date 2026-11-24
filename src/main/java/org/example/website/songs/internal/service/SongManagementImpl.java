package org.example.website.songs.internal.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.audit.api.AuditAction;
import org.example.website.audit.api.AuditEntry;
import org.example.website.audit.api.AuditTrail;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.platform.api.limits.RateLimiter;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.Song;
import org.example.website.songs.api.SongFile;
import org.example.website.songs.api.SongManagement;
import org.example.website.songs.api.SongWithFiles;
import org.example.website.songs.api.UploadTicket;
import org.example.website.songs.internal.config.SongsConfig;
import org.example.website.songs.internal.files.FileInspector;
import org.example.website.songs.internal.files.FileStorage;
import org.example.website.songs.internal.store.SongFileRepository;
import org.example.website.songs.internal.store.SongRepository;
import org.jboss.logging.Logger;

/**
 * What admins do with songs and files (MEM-9 to MEM-21). Every method checks the role first and
 * writes the audit trail, with the IDs and never the names or notes (LGG-10).
 */
@ApplicationScoped
public class SongManagementImpl implements SongManagement {

    private static final Logger LOG = Logger.getLogger(SongManagementImpl.class);
    private static final Duration UPLOAD_VALID = Duration.ofMinutes(15);

    @Inject SongRepository songs;
    @Inject SongFileRepository files;
    @Inject FileStorage storage;
    @Inject AuditTrail audit;
    @Inject RateLimiter limiter;
    @Inject SongsConfig config;
    @Inject Clock clock;

    @Override
    public List<SongWithFiles> all(UserSummary actor) {
        SongRules.requireAdmin(actor);
        List<Song> sorted = new ArrayList<>(songs.findAll());
        // Active songs first (MEM-11), then by the order set, then by title
        sorted.sort(
                Comparator.comparing(Song::archived)
                        .thenComparingInt(Song::displayOrder)
                        .thenComparing(Song::title, String.CASE_INSENSITIVE_ORDER));
        List<SongWithFiles> result = new ArrayList<>();
        for (Song song : sorted) {
            result.add(new SongWithFiles(song, files.findBySong(song.id())));
        }
        return result;
    }

    @Override
    public SongWithFiles find(UUID songId, UserSummary actor) {
        SongRules.requireAdmin(actor);
        Song song = song(songId);
        return new SongWithFiles(song, files.findBySong(songId));
    }

    @Override
    public Song add(String title, String notes, UserSummary actor) {
        SongRules.requireAdmin(actor);
        String cleanTitle = SongRules.title(title);
        String cleanNotes = SongRules.notes(notes);
        int next = songs.findAll().stream().mapToInt(Song::displayOrder).max().orElse(-1) + 1;
        var now = clock.instant();
        Song song = new Song(UUID.randomUUID(), cleanTitle, cleanNotes, false, now, now, next);
        songs.save(song);
        record(AuditAction.SONG_ADD, actor, Map.of("song", song.id().toString()));
        return song;
    }

    @Override
    public Song edit(UUID songId, String title, String notes, UserSummary actor) {
        SongRules.requireAdmin(actor);
        Song old = song(songId);
        Song changed =
                new Song(
                        old.id(),
                        SongRules.title(title),
                        SongRules.notes(notes),
                        old.archived(),
                        old.createdAt(),
                        old.statusChangedAt(),
                        old.displayOrder());
        songs.save(changed);
        record(AuditAction.SONG_EDIT, actor, Map.of("song", songId.toString()));
        return changed;
    }

    @Override
    public Song archive(UUID songId, UserSummary actor) {
        return setArchived(songId, true, AuditAction.SONG_ARCHIVE, actor);
    }

    @Override
    public Song unarchive(UUID songId, UserSummary actor) {
        return setArchived(songId, false, AuditAction.SONG_UNARCHIVE, actor);
    }

    private Song setArchived(UUID songId, boolean archived, AuditAction action, UserSummary actor) {
        SongRules.requireAdmin(actor);
        Song old = song(songId);
        if (old.archived() == archived) {
            return old; // already there: nothing to change
        }
        Song changed =
                new Song(
                        old.id(),
                        old.title(),
                        old.notes(),
                        archived,
                        old.createdAt(),
                        clock.instant(),
                        old.displayOrder());
        songs.save(changed);
        record(action, actor, Map.of("song", songId.toString()));
        return changed;
    }

    @Override
    public void delete(UUID songId, UserSummary actor) {
        SongRules.requireAdmin(actor);
        song(songId);
        for (SongFile file : files.findBySong(songId)) {
            storage.delete(SongRules.objectName(file.id(), file.type()));
            files.deleteFile(file.id());
        }
        songs.delete(songId);
        record(AuditAction.SONG_DELETE, actor, Map.of("song", songId.toString()));
    }

    @Override
    public UploadTicket requestUpload(
            UUID songId, FileType type, String originalName, long sizeBytes, UserSummary actor) {
        SongRules.requireAdmin(actor);
        song(songId);
        if (type == null) {
            throw new InvalidInput("Choose the type of the file");
        }
        SongRules.originalName(originalName, type);
        SongRules.size(sizeBytes);
        var hit = limiter.hit("file-upload:" + actor.id(), Duration.ofHours(1));
        if (hit.count() > config.uploadsPerHour()) {
            LOG.warn("Upload addresses refused: asked too often");
            throw new TooManyRequests("Too many uploads", hit.untilReset().toSeconds());
        }
        UUID fileId = UUID.randomUUID();
        var signed =
                storage.signUpload(
                        SongRules.objectName(fileId, type),
                        type.contentType(),
                        MAX_BYTES,
                        UPLOAD_VALID);
        return new UploadTicket(fileId, signed.url(), signed.headers());
    }

    @Override
    public SongFile completeUpload(
            UUID songId,
            UUID fileId,
            FileType type,
            String originalName,
            String displayName,
            UserSummary actor) {
        SongRules.requireAdmin(actor);
        song(songId);
        if (type == null) {
            throw new InvalidInput("Choose the type of the file");
        }
        String cleanOriginal = SongRules.originalName(originalName, type);
        String cleanName = SongRules.displayName(displayName);
        var existing = files.findFile(fileId);
        if (existing.isPresent()) {
            return existing.get(); // reported twice: the same file
        }
        String objectName = SongRules.objectName(fileId, type);
        byte[] data =
                storage.read(objectName, (int) MAX_BYTES)
                        .orElseThrow(() -> new InvalidInput("The file was not uploaded"));
        if (data.length > MAX_BYTES || !FileInspector.matches(type, data)) {
            storage.delete(objectName);
            LOG.warn("An uploaded file was refused and deleted (not what its type says)");
            throw new InvalidInput(
                    "The file is not a valid " + type.title().toLowerCase() + " file");
        }
        SongFile file =
                new SongFile(
                        fileId,
                        songId,
                        type,
                        cleanName,
                        cleanOriginal,
                        data.length,
                        clock.instant());
        files.save(file);
        record(
                AuditAction.FILE_ADD,
                actor,
                Map.of("song", songId.toString(), "file", fileId.toString()));
        return file;
    }

    @Override
    public SongFile renameFile(UUID fileId, String displayName, UserSummary actor) {
        SongRules.requireAdmin(actor);
        SongFile old = file(fileId);
        SongFile changed =
                new SongFile(
                        old.id(),
                        old.songId(),
                        old.type(),
                        SongRules.displayName(displayName),
                        old.originalName(),
                        old.sizeBytes(),
                        old.uploadedAt());
        files.save(changed);
        record(
                AuditAction.FILE_RENAME,
                actor,
                Map.of("song", old.songId().toString(), "file", fileId.toString()));
        return changed;
    }

    @Override
    public void removeFile(UUID fileId, UserSummary actor) {
        SongRules.requireAdmin(actor);
        SongFile file = file(fileId);
        storage.delete(SongRules.objectName(file.id(), file.type()));
        files.deleteFile(fileId);
        record(
                AuditAction.FILE_REMOVE,
                actor,
                Map.of("song", file.songId().toString(), "file", fileId.toString()));
    }

    // Helpers

    private Song song(UUID id) {
        return songs.find(id).orElseThrow(() -> new ResourceNotFound("No such song"));
    }

    private SongFile file(UUID id) {
        return files.findFile(id).orElseThrow(() -> new ResourceNotFound("No such file"));
    }

    private void record(AuditAction action, UserSummary actor, Map<String, String> details) {
        audit.record(
                new AuditEntry(
                        action,
                        new AuditEntry.Person(actor.id(), actor.email(), actor.role().name()),
                        null,
                        AuditEntry.Result.SUCCESS,
                        details));
    }
}
