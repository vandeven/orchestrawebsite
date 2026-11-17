package org.example.website.platform;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.example.website.platform.api.Access;
import org.example.website.platform.api.AccessLevel;

/** Test only: one endpoint per access level, one without the annotation, one with an override. */
@Path("/test-only/access")
@Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML})
public class AccessProbeResource {

    @GET
    @Path("public")
    @Access(AccessLevel.PUBLIC)
    public String publicPage() {
        return "public";
    }

    @GET
    @Path("member")
    @Access(AccessLevel.MEMBER)
    public String memberPage() {
        return "member";
    }

    @GET
    @Path("admin")
    @Access(AccessLevel.ADMIN)
    public String adminPage() {
        return "admin";
    }

    @GET
    @Path("master")
    @Access(AccessLevel.MASTER)
    public String masterPage() {
        return "master";
    }

    @POST
    @Path("admin-post")
    @Access(AccessLevel.ADMIN)
    public String adminAction() {
        return "done";
    }

    @GET
    @Path("unannotated")
    public String forgotten() {
        return "should never be shown";
    }
}
