package org.example.website.songs.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.website.accounts.api.login.CurrentUser;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.access.Destructive;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.support.AmsterdamTime;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.Song;
import org.example.website.songs.api.SongFile;
import org.example.website.songs.api.SongManagement;
import org.example.website.songs.api.SongWithFiles;
import org.example.website.songs.api.UploadTicket;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;

/**
 * The song management pages for admins and the master (MEM-9 to MEM-21): the overview with active
 * and archived songs, one page per song for editing and its files, and the confirmation pages for
 * deleting. Files are uploaded straight to storage by the browser with an address this class asks
 * for (MEM-18). Everything is checked again in {@link SongManagement}.
 */
@Path("/songs/manage")
@Access(AccessLevel.ADMIN)
public class SongManagementResource {

    @Inject
    @Location("songs/manage.html")
    Template overviewPage;

    @Inject
    @Location("songs/song.html")
    Template songPage;

    @Inject
    @Location("songs/confirm.html")
    Template confirmPage;

    @Inject SongManagement management;
    @Inject CurrentUser currentUser;

    private static final Map<String, String> NOTICES =
            Map.ofEntries(
                    Map.entry(
                            "added",
                            "The song has been added. It is active, so members see it now."),
                    Map.entry("saved", "The changes have been saved."),
                    Map.entry(
                            "archived",
                            "The song is archived. Members no longer see it. Nothing is deleted."),
                    Map.entry("unarchived", "The song is active again. Members see it now."),
                    Map.entry("deleted", "The song and all its files have been deleted."),
                    Map.entry("file-added", "The file has been added."),
                    Map.entry("file-renamed", "The name has been changed."),
                    Map.entry("file-removed", "The file has been removed."));

    /** A file as the pages show it. */
    public record FileRow(
            String id, String type, String name, String original, String size, String uploaded) {}

    /** A choice in the list of file types. */
    public record TypeOption(String code, String title, String extension) {}

    /** A song as the pages show it. */
    public record SongRow(
            String id,
            String title,
            String notes,
            boolean archived,
            String changed,
            List<FileRow> files) {

        public SongRow {
            files = List.copyOf(files);
        }
    }

    // The overview (MEM-11)

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response overview(@RestQuery String notice) {
        List<SongRow> active = new ArrayList<>();
        List<SongRow> archived = new ArrayList<>();
        for (SongWithFiles item : management.all(actor())) {
            (item.song().archived() ? archived : active).add(row(item));
        }
        return page(
                overviewPage
                        .data("currentPath", "/songs/manage")
                        .data("active", active)
                        .data("archived", archived)
                        .data("notice", notice == null ? null : NOTICES.get(notice))
                        .data("error", null)
                        .data("title", "")
                        .data("notes", "")
                        .data("titleMax", SongManagement.TITLE_MAX)
                        .data("notesMax", SongManagement.NOTES_MAX),
                200);
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    public Response add(@RestForm String title, @RestForm String notes) {
        try {
            Song song = management.add(title, notes, actor());
            return redirect("/songs/manage/" + song.id(), "added");
        } catch (InvalidInput e) {
            List<SongRow> active = new ArrayList<>();
            List<SongRow> archived = new ArrayList<>();
            for (SongWithFiles item : management.all(actor())) {
                (item.song().archived() ? archived : active).add(row(item));
            }
            return page(
                    overviewPage
                            .data("currentPath", "/songs/manage")
                            .data("active", active)
                            .data("archived", archived)
                            .data("notice", null)
                            .data("error", e.getMessage())
                            .data("title", title == null ? "" : title)
                            .data("notes", notes == null ? "" : notes)
                            .data("titleMax", SongManagement.TITLE_MAX)
                            .data("notesMax", SongManagement.NOTES_MAX),
                    400);
        }
    }

    // One song (MEM-14, MEM-15, MEM-20)

    @GET
    @Path("{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response song(@PathParam("id") String id, @RestQuery String notice) {
        return songPage(
                uuid(id), notice == null ? null : NOTICES.get(notice), null, null, null, 200);
    }

    @POST
    @Path("{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response edit(
            @PathParam("id") String id, @RestForm String title, @RestForm String notes) {
        UUID songId = uuid(id);
        try {
            management.edit(songId, title, notes, actor());
            return redirect("/songs/manage/" + songId, "saved");
        } catch (InvalidInput e) {
            return songPage(songId, null, e.getMessage(), title, notes, 400);
        }
    }

    @POST
    @Path("{id}/archive")
    public Response archive(@PathParam("id") String id) {
        management.archive(uuid(id), actor());
        return redirect("/songs/manage", "archived");
    }

    @POST
    @Path("{id}/unarchive")
    public Response unarchive(@PathParam("id") String id) {
        management.unarchive(uuid(id), actor());
        return redirect("/songs/manage", "unarchived");
    }

    // Deleting a song (MEM-13)

    @GET
    @Path("{id}/delete")
    @Produces(MediaType.TEXT_HTML)
    public Response confirmDelete(@PathParam("id") String id) {
        SongWithFiles item = management.find(uuid(id), actor());
        return page(
                confirmPage
                        .data("currentPath", "/songs/manage")
                        .data("heading", "Delete the song")
                        .data(
                                "text",
                                "You are about to delete the song “"
                                        + item.song().title()
                                        + "” with its "
                                        + item.files().size()
                                        + " file(s). They are removed from the storage. This cannot be undone.")
                        .data("action", "/songs/manage/" + item.song().id() + "/delete")
                        .data("button", "Delete the song")
                        .data("cancel", "/songs/manage/" + item.song().id()),
                200);
    }

    @POST
    @Path("{id}/delete")
    @Destructive
    public Response delete(@PathParam("id") String id, @RestForm String confirm) {
        management.delete(uuid(id), actor());
        return redirect("/songs/manage", "deleted");
    }

    // Uploading files (MEM-15, MEM-17, MEM-18): two requests from the browser

    @POST
    @Path("{id}/files/upload-url")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadUrl(@PathParam("id") String id, String body) {
        try {
            JsonObject request = json(body);
            UploadTicket ticket =
                    management.requestUpload(
                            uuid(id),
                            FileType.fromCode(request.getString("type")).orElse(null),
                            request.getString("originalName"),
                            request.getLong("size", -1L),
                            actor());
            JsonObject headers = new JsonObject();
            ticket.headers().forEach(headers::put);
            return json(
                    200,
                    new JsonObject()
                            .put("fileId", ticket.fileId().toString())
                            .put("uploadUrl", ticket.uploadUrl())
                            .put("headers", headers));
        } catch (InvalidInput e) {
            return json(400, new JsonObject().put("error", e.getMessage()));
        }
    }

    @POST
    @Path("{id}/files/{fileId}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response complete(
            @PathParam("id") String id, @PathParam("fileId") String fileId, String body) {
        try {
            JsonObject request = json(body);
            SongFile file =
                    management.completeUpload(
                            uuid(id),
                            uuid(fileId),
                            FileType.fromCode(request.getString("type")).orElse(null),
                            request.getString("originalName"),
                            request.getString("displayName"),
                            actor());
            return json(200, new JsonObject().put("id", file.id().toString()));
        } catch (InvalidInput e) {
            return json(400, new JsonObject().put("error", e.getMessage()));
        }
    }

    // Renaming and removing a file (MEM-16, MEM-21)

    @POST
    @Path("files/{fileId}/rename")
    @Produces(MediaType.TEXT_HTML)
    public Response rename(@PathParam("fileId") String fileId, @RestForm String displayName) {
        UUID id = uuid(fileId);
        UUID songId = songOf(id);
        try {
            management.renameFile(id, displayName, actor());
            return redirect("/songs/manage/" + songId, "file-renamed");
        } catch (InvalidInput e) {
            return songPage(songId, null, e.getMessage(), null, null, 400);
        }
    }

    @GET
    @Path("files/{fileId}/remove")
    @Produces(MediaType.TEXT_HTML)
    public Response confirmRemove(@PathParam("fileId") String fileId) {
        UUID id = uuid(fileId);
        UUID songId = songOf(id);
        SongWithFiles item = management.find(songId, actor());
        SongFile file =
                item.files().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
        return page(
                confirmPage
                        .data("currentPath", "/songs/manage")
                        .data("heading", "Remove the file")
                        .data(
                                "text",
                                "You are about to remove the file “"
                                        + file.displayName()
                                        + "” from the song “"
                                        + item.song().title()
                                        + "”. It is removed from the storage. This cannot be undone.")
                        .data("action", "/songs/manage/files/" + file.id() + "/remove")
                        .data("button", "Remove the file")
                        .data("cancel", "/songs/manage/" + songId),
                200);
    }

    @POST
    @Path("files/{fileId}/remove")
    @Destructive
    public Response remove(@PathParam("fileId") String fileId, @RestForm String confirm) {
        UUID id = uuid(fileId);
        UUID songId = songOf(id);
        management.removeFile(id, actor());
        return redirect("/songs/manage/" + songId, "file-removed");
    }

    // Helpers

    private Response songPage(
            UUID songId,
            String notice,
            String error,
            String typedTitle,
            String typedNotes,
            int status) {
        SongWithFiles item = management.find(songId, actor());
        SongRow row = row(item);
        return page(
                songPage.data("currentPath", "/songs/manage")
                        .data("song", row)
                        .data("notice", notice)
                        .data("error", error)
                        .data("title", typedTitle == null ? row.title() : typedTitle)
                        .data("notes", typedNotes == null ? row.notes() : typedNotes)
                        .data("titleMax", SongManagement.TITLE_MAX)
                        .data("notesMax", SongManagement.NOTES_MAX)
                        .data("nameMax", SongManagement.FILE_NAME_MAX)
                        .data(
                                "types",
                                List.of(FileType.values()).stream()
                                        .map(t ->
                                                new TypeOption(t.code(), t.title(), t.extension()))
                                        .toList()),
                status);
    }

    private UUID songOf(UUID fileId) {
        for (SongWithFiles item : management.all(actor())) {
            for (SongFile file : item.files()) {
                if (file.id().equals(fileId)) {
                    return item.song().id();
                }
            }
        }
        throw new ResourceNotFound("No such file");
    }

    private static SongRow row(SongWithFiles item) {
        Song song = item.song();
        List<FileRow> files =
                item.files().stream()
                        .map(f ->
                                new FileRow(
                                        f.id().toString(),
                                        f.type().title(),
                                        f.displayName(),
                                        f.originalName(),
                                        MemberSongsResource.size(f.sizeBytes()),
                                        AmsterdamTime.date(f.uploadedAt())))
                        .toList();
        return new SongRow(
                song.id().toString(),
                song.title(),
                song.notes(),
                song.archived(),
                AmsterdamTime.date(song.statusChangedAt()),
                files);
    }

    private static JsonObject json(String body) {
        try {
            return new JsonObject(body == null ? "{}" : body);
        } catch (RuntimeException e) {
            throw new InvalidInput("The request is not valid");
        }
    }

    private static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFound("No such song or file");
        }
    }

    private static Response json(int status, JsonObject body) {
        return page(Response.status(status).entity(body.encode()).type(MediaType.APPLICATION_JSON));
    }

    private static Response page(io.quarkus.qute.TemplateInstance template, int status) {
        return page(
                Response.status(status).entity(template.render()).type(MediaType.TEXT_HTML_TYPE));
    }

    private static Response page(Response.ResponseBuilder builder) {
        return builder.header("Cache-Control", "no-store").build();
    }

    private Response redirect(String path, String notice) {
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", path + "?notice=" + notice)
                .header("Cache-Control", "no-store")
                .build();
    }

    private UserSummary actor() {
        return currentUser.current().orElseThrow(() -> new AccessDenied("Not logged in"));
    }
}
