package org.example.website.platform;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.access.Destructive;
import org.jboss.resteasy.reactive.RestForm;

/** Test only: destructive endpoints, and counters that show whether the action really ran. */
@Path("/test-only/destructive")
@Access(AccessLevel.PUBLIC)
@Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML})
public class DestructiveProbeResource {

    public static final AtomicInteger DELETIONS = new AtomicInteger();

    @POST
    @Path("delete")
    @Destructive
    public String delete(@RestForm String confirm) {
        DELETIONS.incrementAndGet();
        return "deleted";
    }

    /**
     * A mistake the architecture test forbids: a destructive GET. The guard must still refuse it.
     */
    @GET
    @Path("delete-by-get")
    @Destructive
    public String deleteByGet(@RestForm String confirm) {
        DELETIONS.incrementAndGet();
        return "deleted";
    }
}
