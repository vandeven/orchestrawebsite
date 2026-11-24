package org.example.website.songs.api;

import java.util.List;
import java.util.UUID;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.api.access.DestroysData;

/**
 * What admins and the master do with songs and files (MEM-9 to MEM-21). Every method checks the
 * role of the acting user on the server and writes the audit trail.
 */
public interface SongManagement {

    /** The longest title, notes, and file name (MEM input rules). */
    int TITLE_MAX = 200;

    int NOTES_MAX = 2000;
    int FILE_NAME_MAX = 100;

    /** The largest file: 15 MB (MEM-17). */
    long MAX_BYTES = 15L * 1024 * 1024;

    /** All songs, active first then archived (MEM-11), each with all its files. */
    List<SongWithFiles> all(UserSummary actor);

    SongWithFiles find(UUID songId, UserSummary actor);

    /** A new song is active (MEM-10). */
    Song add(String title, String notes, UserSummary actor);

    /** Changes the title and notes, on active and archived songs (MEM-20). */
    Song edit(UUID songId, String title, String notes, UserSummary actor);

    Song archive(UUID songId, UserSummary actor);

    Song unarchive(UUID songId, UserSummary actor);

    /** Deletes the song and all its files from storage (MEM-13). Needs a confirmation. */
    @DestroysData
    void delete(UUID songId, UserSummary actor);

    /**
     * An address to upload one file to (MEM-18). The extension of {@code originalName} must match
     * the type, and the size must be from 1 byte to 15 MB; the signature enforces the size.
     */
    UploadTicket requestUpload(
            UUID songId, FileType type, String originalName, long sizeBytes, UserSummary actor);

    /**
     * Checks the uploaded file by its content and adds it to the song (MEM-15, MEM-17). A file that
     * is not what it says is deleted at once.
     */
    SongFile completeUpload(
            UUID songId,
            UUID fileId,
            FileType type,
            String originalName,
            String displayName,
            UserSummary actor);

    /** Changes the display name only (MEM-21). */
    SongFile renameFile(UUID fileId, String displayName, UserSummary actor);

    /** Removes one file from its song and from storage (MEM-16). Needs a confirmation. */
    @DestroysData
    void removeFile(UUID fileId, UserSummary actor);
}
