package org.example.website.songs.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.example.website.audit.api.AuditAction;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.Song;
import org.example.website.songs.api.SongFile;
import org.example.website.songs.api.SongManagement;
import org.example.website.songs.api.UploadTicket;
import org.example.website.songs.internal.files.SongFiles;
import org.junit.jupiter.api.Test;

/** Songs and files for admins (MEM-9 to MEM-21). */
class SongManagementTest {

    private final SongsFixture f = new SongsFixture();
    private final SongManagementImpl m = f.management;

    private Song song() {
        return m.add("Ave Maria", "Arr. for choir", f.admin);
    }

    /** What the browser does: ask for an address, upload, report. */
    private SongFile upload(Song song, FileType type, String original, byte[] data, String name) {
        UploadTicket ticket = m.requestUpload(song.id(), type, original, data.length, f.admin);
        f.storage.put("songs/" + ticket.fileId() + "." + type.extension(), data);
        f.now = f.now.plusSeconds(1); // every file has its own upload time
        return m.completeUpload(song.id(), ticket.fileId(), type, original, name, f.admin);
    }

    // Who may

    @Test
    void membersAndAnonymousVisitorsCannotDoAnything() {
        Song song = song();
        UUID file = upload(song, FileType.SHEET_MUSIC, "a.pdf", SongFiles.pdf(), "A").id();
        for (var actor :
                new org.example.website.accounts.api.users.UserSummary[] {f.member, null}) {
            assertThrows(AccessDenied.class, () -> m.all(actor));
            assertThrows(AccessDenied.class, () -> m.find(song.id(), actor));
            assertThrows(AccessDenied.class, () -> m.add("x", "", actor));
            assertThrows(AccessDenied.class, () -> m.edit(song.id(), "x", "", actor));
            assertThrows(AccessDenied.class, () -> m.archive(song.id(), actor));
            assertThrows(AccessDenied.class, () -> m.unarchive(song.id(), actor));
            assertThrows(AccessDenied.class, () -> m.delete(song.id(), actor));
            assertThrows(
                    AccessDenied.class,
                    () -> m.requestUpload(song.id(), FileType.AUDIO, "a.mp3", 10, actor));
            assertThrows(AccessDenied.class, () -> m.renameFile(file, "x", actor));
            assertThrows(AccessDenied.class, () -> m.removeFile(file, actor));
        }
        assertEquals(1, m.all(f.admin).size());
    }

    @Test
    void theMasterMayDoWhatAdminsDo() {
        Song song = m.add("From the master", "", f.master);
        assertEquals(1, m.all(f.master).size());
        m.archive(song.id(), f.master);
    }

    // Songs (MEM-10 to MEM-13, MEM-20)

    @Test
    void aNewSongIsActiveAndHasTheNextPlaceInTheOrder() {
        Song first = song();
        Song second = m.add("Second", "", f.admin);
        assertFalse(first.archived());
        assertEquals(first.displayOrder() + 1, second.displayOrder());
        assertEquals(SongsFixture.START, first.createdAt());
    }

    @Test
    void theTitleAndNotesHaveExactBoundaries() {
        assertEquals(200, m.add("t".repeat(200), "", f.admin).title().length());
        assertThrows(InvalidInput.class, () -> m.add("t".repeat(201), "", f.admin));
        assertThrows(InvalidInput.class, () -> m.add("", "", f.admin));
        assertThrows(InvalidInput.class, () -> m.add("   ", "", f.admin));
        assertThrows(InvalidInput.class, () -> m.add(null, "", f.admin));
        assertThrows(InvalidInput.class, () -> m.add("two\nlines", "", f.admin));
        assertEquals("trimmed", m.add("  trimmed  ", "", f.admin).title());
        assertEquals(2000, m.add("n", "n".repeat(2000), f.admin).notes().length());
        assertThrows(InvalidInput.class, () -> m.add("n", "n".repeat(2001), f.admin));
        assertEquals("a\nb", m.add("n", "a\r\nb", f.admin).notes());
        assertThrows(InvalidInput.class, () -> m.add("n", "bad\u0000char", f.admin));
        assertEquals("", m.add("n", null, f.admin).notes());
    }

    @Test
    void anAdminEditsTheTitleAndNotesOfActiveAndArchivedSongs() {
        Song song = song();
        assertEquals("New title", m.edit(song.id(), "New title", "New notes", f.admin).title());
        m.archive(song.id(), f.admin);
        Song edited = m.edit(song.id(), "Archived title", "", f.admin);
        assertEquals("Archived title", edited.title());
        assertTrue(edited.archived(), "editing does not change the status");
        assertThrows(InvalidInput.class, () -> m.edit(song.id(), "", "", f.admin));
        assertEquals("Archived title", m.find(song.id(), f.admin).song().title());
    }

    @Test
    void archivingAndUnarchivingKeepEverythingAndAreIdempotent() {
        Song song = song();
        upload(song, FileType.AUDIO, "a.mp3", SongFiles.mp3WithTag(), "Recording");
        f.now = f.now.plus(Duration.ofDays(1));
        Song archived = m.archive(song.id(), f.admin);
        assertTrue(archived.archived());
        assertEquals(f.now, archived.statusChangedAt());
        f.now = f.now.plus(Duration.ofDays(1));
        assertEquals(archived, m.archive(song.id(), f.admin), "already archived: nothing changes");
        assertEquals(1, m.find(song.id(), f.admin).files().size(), "the files are kept");
        assertFalse(m.unarchive(song.id(), f.admin).archived());
    }

    @Test
    void allSongsListActiveFirstThenArchivedEachInTheirOrder() {
        Song a = m.add("Bravo", "", f.admin);
        Song b = m.add("Alpha", "", f.admin);
        Song c = m.add("Charlie", "", f.admin);
        m.archive(a.id(), f.admin);
        var order = m.all(f.admin).stream().map(s -> s.song().title()).toList();
        assertEquals(java.util.List.of("Alpha", "Charlie", "Bravo"), order);
        assertNotNull(c);
        assertNotNull(b);
    }

    @Test
    void deletingASongRemovesItsFilesFromStorageAndTheDatabase() {
        Song song = song();
        SongFile file = upload(song, FileType.SHEET_MUSIC, "a.pdf", SongFiles.pdf(), "Part");
        Song other = m.add("Other", "", f.admin);
        upload(other, FileType.SHEET_MUSIC, "b.pdf", SongFiles.pdf(), "Other part");
        m.delete(song.id(), f.admin);
        assertThrows(ResourceNotFound.class, () -> m.find(song.id(), f.admin));
        assertFalse(f.storage.exists("songs/" + file.id() + ".pdf"));
        assertEquals(1, f.storage.count(), "only the other song's file is left");
        assertThrows(ResourceNotFound.class, () -> m.delete(song.id(), f.admin));
    }

    @Test
    void anUnknownSongIsNotFound() {
        UUID unknown = UUID.randomUUID();
        assertThrows(ResourceNotFound.class, () -> m.find(unknown, f.admin));
        assertThrows(ResourceNotFound.class, () -> m.edit(unknown, "x", "", f.admin));
        assertThrows(ResourceNotFound.class, () -> m.archive(unknown, f.admin));
        assertThrows(
                ResourceNotFound.class,
                () -> m.requestUpload(unknown, FileType.AUDIO, "a.mp3", 10, f.admin));
    }

    // Uploads (MEM-15, MEM-17, MEM-18)

    @Test
    void theTicketIsForTheRightTypeWithTheSizeLimitInTheSignature() {
        Song song = song();
        UploadTicket ticket =
                m.requestUpload(song.id(), FileType.AUDIO, "My Song.MP3", 5000, f.admin);
        assertTrue(ticket.uploadUrl().contains("songs/" + ticket.fileId() + ".mp3"));
        assertEquals("audio/mpeg", ticket.headers().get("Content-Type"));
        assertEquals("1,15728640", ticket.headers().get("x-goog-content-length-range"));
        assertFalse(
                ticket.uploadUrl().contains("My"), "the storage path never holds the file name");
    }

    @Test
    void theExtensionMustMatchTheTypeAndTheSizeHasExactBoundaries() {
        Song song = song();
        m.requestUpload(
                song.id(), FileType.SHEET_MUSIC, "a.pdf", SongManagement.MAX_BYTES, f.admin);
        m.requestUpload(song.id(), FileType.SHEET_MUSIC, "a.pdf", 1, f.admin);
        for (long size : new long[] {0, -1, SongManagement.MAX_BYTES + 1}) {
            assertThrows(
                    InvalidInput.class,
                    () -> m.requestUpload(song.id(), FileType.SHEET_MUSIC, "a.pdf", size, f.admin),
                    "size " + size);
        }
        for (String name :
                new String[] {"a.mp3", "a.pdf.exe", "a", ".pdf", "a.txt", "", null, "a\n.pdf"}) {
            assertThrows(
                    InvalidInput.class,
                    () -> m.requestUpload(song.id(), FileType.SHEET_MUSIC, name, 10, f.admin),
                    String.valueOf(name));
        }
        assertThrows(
                InvalidInput.class, () -> m.requestUpload(song.id(), null, "a.pdf", 10, f.admin));
    }

    @Test
    void ticketsAreRateLimited() {
        Song song = song();
        for (int i = 0; i < 200; i++) {
            m.requestUpload(song.id(), FileType.AUDIO, "a.mp3", 10, f.admin);
        }
        assertThrows(
                TooManyRequests.class,
                () -> m.requestUpload(song.id(), FileType.AUDIO, "a.mp3", 10, f.admin));
    }

    @Test
    void everyTypeOfFileCanBeAddedToActiveAndArchivedSongs() {
        Song song = song();
        upload(song, FileType.SHEET_MUSIC, "a.pdf", SongFiles.pdf(), "Soprano");
        upload(song, FileType.AUDIO, "b.mp3", SongFiles.mp3Frame(), "Full recording");
        upload(song, FileType.LYRICS, "c.txt", SongFiles.lyrics(), "Lyrics");
        m.archive(song.id(), f.admin);
        upload(song, FileType.AUDIO, "d.MP3", SongFiles.mp3WithTag(), "Practice track");
        var files = m.find(song.id(), f.admin).files();
        assertEquals(4, files.size());
        assertEquals("d.MP3", files.get(3).originalName());
        assertEquals(SongFiles.lyrics().length, files.get(2).sizeBytes());
    }

    @Test
    void aFileThatIsNotWhatItsTypeSaysIsRefusedAndDeleted() {
        Song song = song();
        byte[][] wrong = {
            SongFiles.mp3WithTag(),
            new byte[] {'N', 'O', 'T', ' ', 'A', ' ', 'P', 'D', 'F'},
            SongFiles.lyrics()
        };
        for (byte[] data : wrong) {
            UploadTicket ticket =
                    m.requestUpload(
                            song.id(), FileType.SHEET_MUSIC, "fake.pdf", data.length, f.admin);
            String object = "songs/" + ticket.fileId() + ".pdf";
            f.storage.put(object, data);
            assertThrows(
                    InvalidInput.class,
                    () ->
                            m.completeUpload(
                                    song.id(),
                                    ticket.fileId(),
                                    FileType.SHEET_MUSIC,
                                    "fake.pdf",
                                    "Part",
                                    f.admin));
            assertFalse(f.storage.exists(object), "removed right away");
        }
        assertTrue(m.find(song.id(), f.admin).files().isEmpty());
    }

    @Test
    void aFileOverFifteenMegabytesIsRefusedAndDeleted() {
        Song song = song();
        UploadTicket ticket =
                m.requestUpload(song.id(), FileType.SHEET_MUSIC, "big.pdf", 1000, f.admin);
        String object = "songs/" + ticket.fileId() + ".pdf";
        f.storage.put(object, SongFiles.of((int) SongManagement.MAX_BYTES + 1, SongFiles.pdf()));
        assertThrows(
                InvalidInput.class,
                () ->
                        m.completeUpload(
                                song.id(),
                                ticket.fileId(),
                                FileType.SHEET_MUSIC,
                                "big.pdf",
                                "Big",
                                f.admin));
        assertFalse(f.storage.exists(object));
    }

    @Test
    void aFileOfExactlyFifteenMegabytesIsAccepted() {
        Song song = song();
        byte[] data = SongFiles.of((int) SongManagement.MAX_BYTES, SongFiles.pdf());
        assertEquals(
                SongManagement.MAX_BYTES,
                upload(song, FileType.SHEET_MUSIC, "max.pdf", data, "Max").sizeBytes());
    }

    @Test
    void aMissingUploadAndABadNameKeepWhatWasUploaded() {
        Song song = song();
        assertThrows(
                InvalidInput.class,
                () ->
                        m.completeUpload(
                                song.id(),
                                UUID.randomUUID(),
                                FileType.AUDIO,
                                "a.mp3",
                                "Name",
                                f.admin));
        UploadTicket ticket = m.requestUpload(song.id(), FileType.AUDIO, "a.mp3", 64, f.admin);
        String object = "songs/" + ticket.fileId() + ".mp3";
        f.storage.put(object, SongFiles.mp3WithTag());
        for (String name : new String[] {"", "  ", null, "n".repeat(101), "two\nlines"}) {
            assertThrows(
                    InvalidInput.class,
                    () ->
                            m.completeUpload(
                                    song.id(),
                                    ticket.fileId(),
                                    FileType.AUDIO,
                                    "a.mp3",
                                    name,
                                    f.admin),
                    String.valueOf(name));
            assertTrue(f.storage.exists(object), "a wrong name does not delete the upload");
        }
        assertEquals(
                100,
                m.completeUpload(
                                song.id(),
                                ticket.fileId(),
                                FileType.AUDIO,
                                "a.mp3",
                                "n".repeat(100),
                                f.admin)
                        .displayName()
                        .length());
    }

    @Test
    void reportingTheSameUploadTwiceGivesTheSameFile() {
        Song song = song();
        byte[] data = SongFiles.pdf();
        UploadTicket ticket =
                m.requestUpload(song.id(), FileType.SHEET_MUSIC, "a.pdf", data.length, f.admin);
        f.storage.put("songs/" + ticket.fileId() + ".pdf", data);
        SongFile first =
                m.completeUpload(
                        song.id(), ticket.fileId(), FileType.SHEET_MUSIC, "a.pdf", "One", f.admin);
        assertEquals(
                first,
                m.completeUpload(
                        song.id(), ticket.fileId(), FileType.SHEET_MUSIC, "a.pdf", "Two", f.admin));
        assertEquals(1, m.find(song.id(), f.admin).files().size());
    }

    @Test
    void theOriginalNameKeepsNoPathAndNoControlCharacters() {
        Song song = song();
        SongFile file =
                upload(
                        song,
                        FileType.SHEET_MUSIC,
                        "C:\\Users\\me\\music\\score.pdf",
                        SongFiles.pdf(),
                        "Score");
        assertEquals("score.pdf", file.originalName());
        SongFile other =
                upload(
                        song,
                        FileType.SHEET_MUSIC,
                        "../../etc/passwd.pdf",
                        SongFiles.pdf(),
                        "Other");
        assertEquals("passwd.pdf", other.originalName());
    }

    // Renaming and removing (MEM-16, MEM-21)

    @Test
    void renamingChangesOnlyTheDisplayName() {
        Song song = song();
        SongFile file = upload(song, FileType.AUDIO, "a.mp3", SongFiles.mp3WithTag(), "Old");
        SongFile renamed = m.renameFile(file.id(), "  Tenor 2  ", f.admin);
        assertEquals("Tenor 2", renamed.displayName());
        assertEquals(file.type(), renamed.type());
        assertEquals(file.originalName(), renamed.originalName());
        assertEquals(file.sizeBytes(), renamed.sizeBytes());
        assertEquals(file.uploadedAt(), renamed.uploadedAt());
        assertThrows(InvalidInput.class, () -> m.renameFile(file.id(), "", f.admin));
        assertThrows(InvalidInput.class, () -> m.renameFile(file.id(), "n".repeat(101), f.admin));
        assertThrows(ResourceNotFound.class, () -> m.renameFile(UUID.randomUUID(), "x", f.admin));
    }

    @Test
    void removingAFileDeletesItFromStorageAndTheSong() {
        Song song = song();
        SongFile keep = upload(song, FileType.SHEET_MUSIC, "a.pdf", SongFiles.pdf(), "Keep");
        SongFile remove = upload(song, FileType.AUDIO, "b.mp3", SongFiles.mp3WithTag(), "Remove");
        m.removeFile(remove.id(), f.admin);
        assertFalse(f.storage.exists("songs/" + remove.id() + ".mp3"));
        assertTrue(f.storage.exists("songs/" + keep.id() + ".pdf"));
        assertEquals(1, m.find(song.id(), f.admin).files().size());
        assertThrows(ResourceNotFound.class, () -> m.removeFile(remove.id(), f.admin));
    }

    // Audit (LGG-9, LGG-10)

    @Test
    void everyChangeIsInTheAuditTrailWithIdsAndNeverNamesOrNotes() {
        Song song = m.add("Secret title", "Secret notes", f.admin);
        m.edit(song.id(), "Other secret", "More", f.admin);
        SongFile file =
                upload(song, FileType.SHEET_MUSIC, "secret.pdf", SongFiles.pdf(), "Secret part");
        m.renameFile(file.id(), "Renamed secret", f.admin);
        m.archive(song.id(), f.admin);
        m.unarchive(song.id(), f.admin);
        m.removeFile(file.id(), f.admin);
        m.delete(song.id(), f.admin);
        var actions = f.audited.stream().map(e -> e.action()).toList();
        assertEquals(
                java.util.List.of(
                        AuditAction.SONG_ADD,
                        AuditAction.SONG_EDIT,
                        AuditAction.FILE_ADD,
                        AuditAction.FILE_RENAME,
                        AuditAction.SONG_ARCHIVE,
                        AuditAction.SONG_UNARCHIVE,
                        AuditAction.FILE_REMOVE,
                        AuditAction.SONG_DELETE),
                actions);
        for (var entry : f.audited) {
            assertEquals(f.admin.email(), entry.actor().email());
            assertTrue(entry.details().get("song").equals(song.id().toString()));
            assertFalse(entry.details().toString().toLowerCase().contains("secret"));
        }
    }
}
