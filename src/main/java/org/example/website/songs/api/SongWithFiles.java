package org.example.website.songs.api;

import java.util.List;

/** A song with its files, newest upload last within each type (MEM-3, MEM-5). */
public record SongWithFiles(Song song, List<SongFile> files) {

    public SongWithFiles {
        files = List.copyOf(files);
    }

    public List<SongFile> files(FileType type) {
        return files.stream().filter(file -> file.type() == type).toList();
    }
}
