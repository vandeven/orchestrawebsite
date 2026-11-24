package org.example.website.content.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.website.accounts.api.login.CurrentUser;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.content.api.images.Image;
import org.example.website.content.api.images.ImageInfo;
import org.example.website.content.api.images.ImageLibrary;
import org.example.website.content.api.images.UploadTicket;
import org.example.website.content.api.texts.TextKey;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.access.Destructive;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.support.AmsterdamTime;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

/**
 * The image library for admins and the master (EDT-9 to EDT-17): the overview, the two requests the
 * browser makes to upload an image straight to storage, and deleting with a confirmation. The
 * browser resizes the image first (EDT-11); the server checks the file itself (HRD-4).
 */
@Path("/edit/images")
@Access(AccessLevel.ADMIN)
public class ImageResource {

    @Inject
    @Location("content/images.html")
    Template overviewPage;

    @Inject
    @Location("content/image-delete.html")
    Template deletePage;

    @Inject ImageLibrary library;
    @Inject CurrentUser currentUser;

    private static final Map<String, String> NOTICES =
            Map.of(
                    "added", "The image has been added to the library.",
                    "deleted", "The image has been deleted for good.");

    /** One image in the overview, with the size in kilobytes and the date in Amsterdam time. */
    public record Row(
            String id,
            String url,
            String alt,
            boolean decorative,
            int width,
            int height,
            String size,
            String uploaded,
            boolean unused,
            String usedIn) {}

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response overview(@RestQuery String notice) {
        List<Row> rows = new ArrayList<>();
        for (ImageInfo info : library.list()) {
            rows.add(row(info));
        }
        String html =
                overviewPage
                        .data("currentPath", "/edit")
                        .data("rows", rows)
                        .data("notice", notice == null ? null : NOTICES.get(notice))
                        .data("altMax", ImageLibrary.ALT_MAX)
                        .render();
        return noStore(Response.ok(html, MediaType.TEXT_HTML_TYPE)).build();
    }

    // The two requests of an upload (HOS-14): an address to upload to, and "it is there"

    @POST
    @Path("upload-url")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadUrl(String body) {
        try {
            JsonObject request = json(body);
            UploadTicket ticket =
                    library.requestUpload(
                            request.getString("contentType"),
                            request.getLong("size", -1L),
                            actor());
            JsonObject headers = new JsonObject();
            ticket.headers().forEach(headers::put);
            return json(
                    200,
                    new JsonObject()
                            .put("id", ticket.id().toString())
                            .put("uploadUrl", ticket.uploadUrl())
                            .put("headers", headers));
        } catch (InvalidInput e) {
            return problem(e);
        }
    }

    @POST
    @Path("{id}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response complete(@RestPath String id, String body) {
        try {
            JsonObject request = json(body);
            Image image =
                    library.complete(
                            uuid(id),
                            request.getString("contentType"),
                            request.getString("alt"),
                            Boolean.TRUE.equals(request.getBoolean("decorative")),
                            actor());
            return json(
                    200,
                    new JsonObject()
                            .put("id", image.id().toString())
                            .put("url", image.url())
                            .put("alt", image.alt())
                            .put("width", image.width())
                            .put("height", image.height()));
        } catch (InvalidInput e) {
            return problem(e);
        }
    }

    // Deleting (EDT-16): a confirmation that names the texts that still use the image

    @GET
    @Path("{id}/delete")
    @Produces(MediaType.TEXT_HTML)
    public Response confirmDelete(@RestPath String id) {
        ImageInfo info =
                library.find(uuid(id)).orElseThrow(() -> new ResourceNotFound("No such image"));
        String html =
                deletePage
                        .data("currentPath", "/edit")
                        .data("image", row(info))
                        .data("used", !info.unused())
                        .render();
        return noStore(Response.ok(html, MediaType.TEXT_HTML_TYPE)).build();
    }

    @POST
    @Path("{id}/delete")
    @Destructive
    public Response delete(@RestPath String id, @RestForm String confirm) {
        library.delete(uuid(id), actor());
        return noStore(
                        Response.status(Response.Status.SEE_OTHER)
                                .header("Location", "/edit/images?notice=deleted"))
                .build();
    }

    // Helpers

    static Row row(ImageInfo info) {
        Image image = info.image();
        List<String> names = info.usedIn().stream().map(TextKey::title).toList();
        return new Row(
                image.id().toString(),
                image.url(),
                image.alt(),
                image.alt().isEmpty(),
                image.width(),
                image.height(),
                size(image.sizeBytes()),
                AmsterdamTime.date(image.uploadedAt()),
                info.unused(),
                String.join(", ", names));
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }
        return String.format(java.util.Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024.0));
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
            throw new ResourceNotFound("No such image");
        }
    }

    /** The messages of {@link InvalidInput} for images are fixed texts for people, never values. */
    private static Response problem(InvalidInput e) {
        return json(400, new JsonObject().put("error", e.getMessage()));
    }

    private static Response json(int status, JsonObject body) {
        return noStore(
                        Response.status(status)
                                .entity(body.encode())
                                .type(MediaType.APPLICATION_JSON))
                .build();
    }

    private static Response.ResponseBuilder noStore(Response.ResponseBuilder builder) {
        return builder.header("Cache-Control", "no-store");
    }

    private UserSummary actor() {
        return currentUser.current().orElseThrow(() -> new AccessDenied("Not logged in"));
    }
}
