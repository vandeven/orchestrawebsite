package org.example.website.platform;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.jboss.logging.MDC;

/** Test only: shows which request ID the logging context holds while a request is handled. */
@Path("/test-only/request-id")
@Access(AccessLevel.PUBLIC)
public class MdcProbeResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String requestIdInLoggingContext() {
        return String.valueOf(MDC.get("requestId"));
    }
}
