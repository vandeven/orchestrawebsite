package org.example.website.songs.internal.service;

import java.util.Locale;
import java.util.UUID;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.SongManagement;

/** The input rules of songs and files (MEM input rules). Messages are fixed texts, never values. */
final class SongRules {

    private SongRules() {}

    static void requireAdmin(UserSummary actor) {
        if (actor == null || (actor.role() != Role.ADMIN && actor.role() != Role.MASTER)) {
            throw new AccessDenied("Songs are managed by admins");
        }
    }

    static boolean isAdmin(UserSummary viewer) {
        return viewer != null && (viewer.role() == Role.ADMIN || viewer.role() == Role.MASTER);
    }

    static void requireMember(UserSummary viewer) {
        if (viewer == null) {
            throw new AccessDenied("Not logged in");
        }
    }

    static String title(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > SongManagement.TITLE_MAX) {
            throw new InvalidInput("The title must be 1 to 200 characters");
        }
        if (text.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidInput("The title must be one line");
        }
        return text;
    }

    /** Plain text, may have several lines; other control characters are refused. */
    static String notes(String value) {
        String text = value == null ? "" : value.strip().replace("\r\n", "\n").replace('\r', '\n');
        if (text.length() > SongManagement.NOTES_MAX) {
            throw new InvalidInput("The notes can have at most 2,000 characters");
        }
        if (text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) {
            throw new InvalidInput("The notes have a character that is not allowed");
        }
        return text;
    }

    static String displayName(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > SongManagement.FILE_NAME_MAX) {
            throw new InvalidInput("The name must be 1 to 100 characters");
        }
        if (text.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidInput("The name must be one line");
        }
        return text;
    }

    /** The name of the uploaded file, for display only: no path, no control characters, short. */
    static String originalName(String value, FileType type) {
        String name = value == null ? "" : value;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).strip();
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidInput("The file name is not valid");
        }
        String extension = "." + type.extension();
        if (name.length() <= extension.length()
                || !name.toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new InvalidInput("The file must end in " + extension + " for this type");
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    static void size(long sizeBytes) {
        if (sizeBytes < 1 || sizeBytes > SongManagement.MAX_BYTES) {
            throw new InvalidInput("The file must be from 1 byte to 15 MB");
        }
    }

    static String objectName(UUID fileId, FileType type) {
        return "songs/" + fileId + "." + type.extension();
    }
}
