package org.example.website.platform;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.example.website.platform.api.Access;
import org.example.website.platform.api.AccessDenied;
import org.example.website.platform.api.AccessLevel;
import org.example.website.platform.api.Conflict;
import org.example.website.platform.api.InvalidInput;
import org.example.website.platform.api.ResourceNotFound;
import org.example.website.platform.api.ServiceUnavailable;

/** Test only: endpoints that fail in every way the website must handle. */
@Path("/test-only/errors")
@Access(AccessLevel.PUBLIC)
public class ErrorProbeResource {

    public static final String SECRET = "internal-detail-4711";

    @GET
    @Path("denied")
    @Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    public String denied() {
        throw new AccessDenied(SECRET);
    }

    @GET
    @Path("missing")
    @Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    public String missing() {
        throw new ResourceNotFound(SECRET);
    }

    @GET
    @Path("invalid")
    @Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    public String invalid() {
        throw new InvalidInput(SECRET);
    }

    @GET
    @Path("conflict")
    @Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    public String conflict() {
        throw new Conflict(SECRET);
    }

    @GET
    @Path("unavailable")
    @Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    public String unavailable() {
        throw new ServiceUnavailable(SECRET);
    }

    @GET
    @Path("crash")
    @Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    public String crash() {
        throw new IllegalStateException(SECRET);
    }

    @POST
    @Path("post-only")
    public String postOnly() {
        return "ok";
    }
}
