package org.example.website.songs.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.example.website.accounts.api.login.CurrentUser;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.songs.api.FileLink;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.SongCatalog;
import org.example.website.songs.api.SongFile;
import org.example.website.songs.api.SongWithFiles;

/**
 * The member page (MEM-1 to MEM-8): the active songs with their files, and the two addresses that
 * lead to a file: one to download it and one for the audio player. Both check access and then send
 * the browser on to a short-lived signed address of the file storage, so the files themselves never
 * pass through the application and a link that is shared stops working (HOS-13).
 */
@Path("/members")
@Access(AccessLevel.MEMBER)
public class MemberSongsResource {

    @Inject
    @Location("songs/members.html")
    Template page;

    @Inject SongCatalog catalog;
    @Inject CurrentUser currentUser;

    /** One file as the page shows it. */
    public record FileRow(String id, String name, String size) {}

    /** The files of one type of a song. */
    public record Group(String title, boolean audio, List<FileRow> files) {

        public Group {
            files = List.copyOf(files);
        }
    }

    /** One song as the page shows it. */
    public record SongRow(String id, String title, String notes, List<Group> groups) {

        public SongRow {
            groups = List.copyOf(groups);
        }
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response members() {
        List<SongRow> rows = new ArrayList<>();
        for (SongWithFiles item : catalog.activeSongs(viewer())) {
            rows.add(row(item));
        }
        String html = page.data("currentPath", "/members").data("songs", rows).render();
        return Response.ok(html, MediaType.TEXT_HTML_TYPE)
                .header("Cache-Control", "no-store")
                .build();
    }

    @GET
    @Path("files/{id}/download")
    public Response download(@jakarta.ws.rs.PathParam("id") String id) {
        return redirect(id, false);
    }

    @GET
    @Path("files/{id}/play")
    public Response play(@jakarta.ws.rs.PathParam("id") String id) {
        return redirect(id, true);
    }

    private Response redirect(String id, boolean inline) {
        FileLink link = catalog.link(uuid(id), inline, viewer());
        return Response.status(Response.Status.SEE_OTHER)
                .location(URI.create(link.url()))
                .header("Cache-Control", "no-store")
                .build();
    }

    // Helpers

    private static SongRow row(SongWithFiles item) {
        List<Group> groups = new ArrayList<>();
        for (FileType type : FileType.values()) {
            List<FileRow> files =
                    item.files(type).stream().map(MemberSongsResource::fileRow).toList();
            if (!files.isEmpty()) {
                groups.add(new Group(type.title(), type == FileType.AUDIO, files));
            }
        }
        return new SongRow(
                item.song().id().toString(), item.song().title(), item.song().notes(), groups);
    }

    private static FileRow fileRow(SongFile file) {
        return new FileRow(file.id().toString(), file.displayName(), size(file.sizeBytes()));
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }
        return String.format(java.util.Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFound("No such file");
        }
    }

    private UserSummary viewer() {
        return currentUser.current().orElseThrow(() -> new AccessDenied("Not logged in"));
    }
}
