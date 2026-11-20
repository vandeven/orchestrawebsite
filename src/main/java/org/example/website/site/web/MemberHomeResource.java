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

/** Temporary member page, replaced by the real member page in phase 3. */
@Path("/members")
@Access(AccessLevel.MEMBER)
public class MemberHomeResource {

    @Inject
    @Location("site/members.html")
    Template members;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance show() {
        return members.data("currentPath", "/members");
    }
}
