package org.example.website.site.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;

/** Temporary first page, replaced by the About page in phase 2. */
@Path("/")
@Access(AccessLevel.PUBLIC)
public class HomeResource {

    @Inject
    @Location("site/home.html")
    Template home;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance home() {
        return home.data("currentPath", "/");
    }
}
