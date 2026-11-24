package org.example.website.songs.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.songs.api.FileLink;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.Song;
import org.example.website.songs.api.SongFile;
import org.example.website.songs.api.SongWithFiles;
import org.example.website.songs.api.UploadTicket;
import org.example.website.songs.internal.files.SongFiles;
import org.junit.jupiter.api.Test;

/** What members see and download (MEM-1 to MEM-8). */
class SongCatalogTest {

    private final SongsFixture f = new SongsFixture();

    private SongFile add(Song song, FileType type, String name, byte[] data) {
        String original = "x." + type.extension();
        UploadTicket ticket =
                f.management.requestUpload(song.id(), type, original, data.length, f.admin);
        f.storage.put("songs/" + ticket.fileId() + "." + type.extension(), data);
        f.now = f.now.plusSeconds(1);
        return f.management.completeUpload(
                song.id(), ticket.fileId(), type, original, name, f.admin);
    }

    @Test
    void membersSeeActiveSongsOnlyAndNeverArchivedOnes() {
        Song active = f.management.add("Active", "", f.admin);
        Song archived = f.management.add("Archived", "", f.admin);
        f.management.archive(archived.id(), f.admin);
        List<SongWithFiles> list = f.catalog.activeSongs(f.member);
        assertEquals(List.of(active.id()), list.stream().map(s -> s.song().id()).toList());
        assertEquals(
                List.of(active.id()),
                f.catalog.activeSongs(f.admin).stream().map(s -> s.song().id()).toList(),
                "admins too: archived songs are on the management page");
    }

    @Test
    void anonymousVisitorsGetNothing() {
        assertThrows(AccessDenied.class, () -> f.catalog.activeSongs(null));
        assertThrows(AccessDenied.class, () -> f.catalog.link(UUID.randomUUID(), false, null));
    }

    @Test
    void filesAreGroupedByTypeWithTheirNames() {
        Song song = f.management.add("Song", "", f.admin);
        add(song, FileType.LYRICS, "Lyrics", SongFiles.lyrics());
        add(song, FileType.AUDIO, "Full recording", SongFiles.mp3WithTag());
        add(song, FileType.SHEET_MUSIC, "Tenor", SongFiles.pdf());
        add(song, FileType.SHEET_MUSIC, "Alto", SongFiles.pdf());
        SongWithFiles shown = f.catalog.activeSongs(f.member).get(0);
        assertEquals(
                List.of("Alto", "Tenor"),
                shown.files(FileType.SHEET_MUSIC).stream().map(SongFile::displayName).toList());
        assertEquals(
                List.of("Full recording"),
                shown.files(FileType.AUDIO).stream().map(SongFile::displayName).toList());
        assertEquals(
                List.of("Lyrics"),
                shown.files(FileType.LYRICS).stream().map(SongFile::displayName).toList());
        assertEquals(
                List.of(
                        FileType.SHEET_MUSIC,
                        FileType.SHEET_MUSIC,
                        FileType.AUDIO,
                        FileType.LYRICS),
                shown.files().stream().map(SongFile::type).toList(),
                "sheet music, then audio, then lyrics");
    }

    @Test
    void songsAreInTheOrderTheAdminsSet() {
        f.management.add("Charlie", "", f.admin);
        f.management.add("Alpha", "", f.admin);
        assertEquals(
                List.of("Charlie", "Alpha"),
                f.catalog.activeSongs(f.member).stream().map(s -> s.song().title()).toList());
    }

    @Test
    void aMemberGetsASignedLinkThatExpiresAndIsAnAttachment() {
        Song song = f.management.add("Song", "", f.admin);
        SongFile file = add(song, FileType.SHEET_MUSIC, "Soprano 1", SongFiles.pdf());
        FileLink link = f.catalog.link(file.id(), false, f.member);
        assertTrue(
                link.url()
                        .startsWith(
                                "https://files.example.test/download/songs/" + file.id() + ".pdf"));
        assertTrue(link.url().contains("disposition=attachment%3B"), link.url());
        assertTrue(link.url().contains("type=application%2Fpdf"));
        assertTrue(link.url().contains("valid=15"));
        assertEquals(f.now.plus(Duration.ofMinutes(15)), link.expiresAt());
    }

    @Test
    void onlyAudioCanBePlayedInlineEverythingElseIsAnAttachment() {
        Song song = f.management.add("Song", "", f.admin);
        SongFile audio = add(song, FileType.AUDIO, "Recording", SongFiles.mp3WithTag());
        SongFile pdf = add(song, FileType.SHEET_MUSIC, "Part", SongFiles.pdf());
        SongFile text = add(song, FileType.LYRICS, "Words", SongFiles.lyrics());
        assertTrue(
                f.catalog.link(audio.id(), true, f.member).url().contains("disposition=inline%3B"));
        assertTrue(
                f.catalog
                        .link(audio.id(), false, f.member)
                        .url()
                        .contains("disposition=attachment%3B"));
        assertTrue(
                f.catalog
                        .link(pdf.id(), true, f.member)
                        .url()
                        .contains("disposition=attachment%3B"));
        assertTrue(
                f.catalog
                        .link(text.id(), true, f.member)
                        .url()
                        .contains("disposition=attachment%3B"));
    }

    @Test
    void theDownloadNameComesFromTheDisplayNameNeverTheUploadedName() {
        Song song = f.management.add("Song", "", f.admin);
        SongFile file = add(song, FileType.SHEET_MUSIC, "Trumpet 1", SongFiles.pdf());
        String url = f.catalog.link(file.id(), false, f.member).url();
        assertTrue(url.contains("Trumpet+1.pdf"), url);
        assertFalse(url.contains("x.pdf"));
    }

    @Test
    void membersGetNoLinkForAFileOfAnArchivedSongEvenWithTheId() {
        Song song = f.management.add("Song", "", f.admin);
        SongFile file = add(song, FileType.AUDIO, "Recording", SongFiles.mp3WithTag());
        f.management.archive(song.id(), f.admin);
        assertThrows(ResourceNotFound.class, () -> f.catalog.link(file.id(), true, f.member));
        assertThrows(ResourceNotFound.class, () -> f.catalog.link(file.id(), false, f.member));
        // The same answer as for a file that does not exist
        assertThrows(
                ResourceNotFound.class, () -> f.catalog.link(UUID.randomUUID(), false, f.member));
        // Admins can still reach it, to check what they keep
        f.catalog.link(file.id(), false, f.admin);
        // After unarchiving members get it again
        f.management.unarchive(song.id(), f.admin);
        f.catalog.link(file.id(), true, f.member);
    }

    @Test
    void aRemovedFileGivesNoLinkAndChangesShowRightAway() {
        Song song = f.management.add("Song", "", f.admin);
        SongFile file = add(song, FileType.SHEET_MUSIC, "Part", SongFiles.pdf());
        assertEquals(1, f.catalog.activeSongs(f.member).get(0).files().size());
        f.management.renameFile(file.id(), "Renamed", f.admin);
        assertEquals(
                "Renamed", f.catalog.activeSongs(f.member).get(0).files().get(0).displayName());
        f.management.removeFile(file.id(), f.admin);
        assertTrue(f.catalog.activeSongs(f.member).get(0).files().isEmpty());
        assertThrows(ResourceNotFound.class, () -> f.catalog.link(file.id(), false, f.member));
    }
}
