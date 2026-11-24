package org.example.website.songs.internal.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.songs.api.FileLink;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.Song;
import org.example.website.songs.api.SongCatalog;
import org.example.website.songs.api.SongFile;
import org.example.website.songs.api.SongWithFiles;
import org.example.website.songs.internal.files.ContentDisposition;
import org.example.website.songs.internal.files.FileStorage;
import org.example.website.songs.internal.store.SongFileRepository;
import org.example.website.songs.internal.store.SongRepository;

/**
 * What members see (MEM-1 to MEM-8). The check that a song is active is made here, on the server,
 * for every list and every link: knowing an ID is never enough (MEM-2).
 */
@ApplicationScoped
public class SongCatalogImpl implements SongCatalog {

    /** Short-lived addresses (HOS-13). */
    static final Duration LINK_VALID = Duration.ofMinutes(15);

    @Inject SongRepository songs;
    @Inject SongFileRepository files;
    @Inject FileStorage storage;
    @Inject Clock clock;

    @Override
    public List<SongWithFiles> activeSongs(UserSummary viewer) {
        SongRules.requireMember(viewer);
        List<Song> active =
                new ArrayList<>(songs.findAll().stream().filter(s -> !s.archived()).toList());
        active.sort(
                Comparator.comparingInt(Song::displayOrder)
                        .thenComparing(Song::title, String.CASE_INSENSITIVE_ORDER));
        List<SongWithFiles> result = new ArrayList<>();
        for (Song song : active) {
            List<SongFile> sorted = new ArrayList<>(files.findBySong(song.id()));
            sorted.sort(
                    Comparator.comparing(SongFile::type)
                            .thenComparing(SongFile::displayName, String.CASE_INSENSITIVE_ORDER));
            result.add(new SongWithFiles(song, sorted));
        }
        return result;
    }

    @Override
    public FileLink link(UUID fileId, boolean inline, UserSummary viewer) {
        SongRules.requireMember(viewer);
        SongFile file =
                files.findFile(fileId).orElseThrow(() -> new ResourceNotFound("No such file"));
        Song song =
                songs.find(file.songId()).orElseThrow(() -> new ResourceNotFound("No such file"));
        if (song.archived() && !SongRules.isAdmin(viewer)) {
            throw new ResourceNotFound(
                    "No such file"); // MEM-2: the same answer as for a missing file
        }
        // Only audio is played in the page; everything else is always an attachment (HRD-4)
        boolean playInline = inline && file.type() == FileType.AUDIO;
        String url =
                storage.signDownload(
                        SongRules.objectName(file.id(), file.type()),
                        file.type().contentType(),
                        ContentDisposition.of(
                                file.displayName(), file.type().extension(), playInline),
                        LINK_VALID);
        return new FileLink(url, clock.instant().plus(LINK_VALID));
    }
}
