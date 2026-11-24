package org.example.website.songs.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.Song;
import org.example.website.songs.api.SongFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** One set of cases for the in-memory and the Firestore song stores (ARC-6). */
public abstract class SongStoreContract {

    protected static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");

    protected abstract SongRepository songs();

    protected abstract SongFileRepository files();

    /** Removes everything, so each case starts from nothing. */
    protected abstract void reset();

    @BeforeEach
    void startClean() {
        reset();
    }

    private static Song song(String title, boolean archived, int order) {
        return new Song(
                UUID.randomUUID(),
                title,
                "Notes of " + title,
                archived,
                T0,
                T0.plusSeconds(5),
                order);
    }

    private static SongFile file(Song song, String name, int secondsLater) {
        return new SongFile(
                UUID.randomUUID(),
                song.id(),
                FileType.AUDIO,
                name,
                "orig.mp3",
                1234,
                T0.plusSeconds(secondsLater));
    }

    @Test
    void aSavedSongComesBackWithEveryField() {
        Song song = song("Ave Maria", false, 3);
        songs().save(song);
        assertEquals(song, songs().find(song.id()).orElseThrow());
    }

    @Test
    void savingAgainReplacesTheSong() {
        Song song = song("Old", false, 0);
        songs().save(song);
        Song changed =
                new Song(song.id(), "New", "", true, song.createdAt(), T0.plusSeconds(99), 7);
        songs().save(changed);
        assertEquals(changed, songs().find(song.id()).orElseThrow());
        assertEquals(1, songs().findAll().size());
    }

    @Test
    void findAllReturnsEverySongAndAnUnknownSongIsNotFound() {
        songs().save(song("One", false, 0));
        songs().save(song("Two", true, 1));
        assertEquals(2, songs().findAll().size());
        assertTrue(songs().find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void deletingASongRemovesItAndIsNotAnErrorTwice() {
        Song song = song("Gone", false, 0);
        songs().save(song);
        songs().delete(song.id());
        songs().delete(song.id());
        assertTrue(songs().find(song.id()).isEmpty());
    }

    @Test
    void aSavedFileComesBackWithEveryField() {
        Song song = song("Song", false, 0);
        SongFile file = file(song, "Recording", 1);
        files().save(file);
        assertEquals(file, files().findFile(file.id()).orElseThrow());
    }

    @Test
    void theFilesOfASongAreOldestFirstAndOnlyThatSongs() {
        Song one = song("One", false, 0);
        Song two = song("Two", false, 1);
        SongFile late = file(one, "Late", 30);
        SongFile early = file(one, "Early", 10);
        files().save(late);
        files().save(early);
        files().save(file(two, "Other song", 20));
        assertEquals(List.of(early, late), files().findBySong(one.id()));
        assertEquals(1, files().findBySong(two.id()).size());
        assertTrue(files().findBySong(UUID.randomUUID()).isEmpty());
    }

    @Test
    void deletingAFileRemovesOnlyThatFile() {
        Song song = song("Song", false, 0);
        SongFile keep = file(song, "Keep", 1);
        SongFile remove = file(song, "Remove", 2);
        files().save(keep);
        files().save(remove);
        files().deleteFile(remove.id());
        files().deleteFile(remove.id());
        assertEquals(List.of(keep), files().findBySong(song.id()));
    }

    @Test
    void renamingAFileKeepsItsPlaceInTheList() {
        Song song = song("Song", false, 0);
        SongFile first = file(song, "First", 1);
        SongFile second = file(song, "Second", 2);
        files().save(first);
        files().save(second);
        SongFile renamed =
                new SongFile(
                        first.id(),
                        song.id(),
                        first.type(),
                        "Renamed",
                        first.originalName(),
                        first.sizeBytes(),
                        first.uploadedAt());
        files().save(renamed);
        assertEquals(List.of(renamed, second), files().findBySong(song.id()));
    }
}
