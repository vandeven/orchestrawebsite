package org.example.website.platform.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Temporary first page, replaced by the About page in phase 2. */
@Path("/")
public class PageResource {

    @Inject
    @Location("site/placeholder.html")
    Template placeholder;

    @ConfigProperty(name = "website.name")
    String name;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance home() {
        return placeholder.data("name", name);
    }
}
