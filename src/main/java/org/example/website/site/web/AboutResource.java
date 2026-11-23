package org.example.website.site.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.example.website.content.api.texts.EditableText;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;

/**
 * The About page, the home page of the website (ABO-1 to ABO-8): one editable text under a fixed
 * title. The old address {@code /about} sends visitors on for good.
 */
@Path("/")
@Access(AccessLevel.PUBLIC)
public class AboutResource {

    /** Short browser cache for anonymous visitors (ABO-7). Logged-in visitors get no-store. */
    static final String PUBLIC_CACHE = "public, max-age=60";

    @Inject
    @Location("site/about.html")
    Template about;

    @Inject EditableTexts texts;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response home() {
        EditableText text = texts.get(TextKey.ABOUT);
        return Response.ok(about.data("currentPath", "/").data("text", text))
                .header("Cache-Control", PUBLIC_CACHE)
                .header("Vary", "Cookie")
                .build();
    }

    @GET
    @Path("about")
    public Response oldAddress() {
        return Response.status(Response.Status.MOVED_PERMANENTLY).location(URI.create("/")).build();
    }
}
