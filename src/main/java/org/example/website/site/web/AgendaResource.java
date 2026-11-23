package org.example.website.site.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;

/** The Agenda page (AGE-1 to AGE-3): one editable text, or a friendly message when it is empty. */
@Path("/agenda")
@Access(AccessLevel.PUBLIC)
public class AgendaResource {

    @Inject
    @Location("site/agenda.html")
    Template page;

    @Inject EditableTexts texts;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response agenda() {
        return Response.ok(
                        page.data("currentPath", "/agenda").data("text", texts.get(TextKey.AGENDA)))
                .header("Cache-Control", AboutResource.PUBLIC_CACHE)
                .header("Vary", "Cookie")
                .build();
    }
}
