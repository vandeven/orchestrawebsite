package org.example.website.songs.api;

import java.util.Optional;

/** The three kinds of file a song can have, with what each must be (MEM-3, MEM-17). */
public enum FileType {
    SHEET_MUSIC("sheet-music", "Sheet music", "pdf", "application/pdf"),
    AUDIO("audio", "Audio", "mp3", "audio/mpeg"),
    LYRICS("lyrics", "Lyrics", "txt", "text/plain");

    private final String code;
    private final String title;
    private final String extension;
    private final String contentType;

    FileType(String code, String title, String extension, String contentType) {
        this.code = code;
        this.title = title;
        this.extension = extension;
        this.contentType = contentType;
    }

    /** The name in forms, addresses and the database. */
    public String code() {
        return code;
    }

    /** The name in the pages. */
    public String title() {
        return title;
    }

    /** The only file extension allowed for this type, without the dot. */
    public String extension() {
        return extension;
    }

    /** The media type the file is stored and served with. */
    public String contentType() {
        return contentType;
    }

    public static Optional<FileType> fromCode(String code) {
        for (FileType type : values()) {
            if (type.code.equals(code)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
