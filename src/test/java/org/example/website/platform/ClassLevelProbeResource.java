package org.example.website.platform;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;

/** Test only: an annotation on the class applies to its methods, and a method can override it. */
@Path("/test-only/class-level")
@Access(AccessLevel.MEMBER)
@Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML})
public class ClassLevelProbeResource {

    @GET
    @Path("inherited")
    public String inherited() {
        return "inherited";
    }

    @GET
    @Path("overridden")
    @Access(AccessLevel.ADMIN)
    public String overridden() {
        return "overridden";
    }
}
