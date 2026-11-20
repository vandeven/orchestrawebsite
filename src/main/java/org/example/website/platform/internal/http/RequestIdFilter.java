package org.example.website.platform.internal.http;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.UUID;
import org.example.website.platform.api.request.RequestIds;
import org.jboss.logging.MDC;

/**
 * Gives every request a server-generated ID (LGG-3). An ID sent by the browser is never trusted.
 * The ID is in the response header and in every log line of the request.
 */
@ApplicationScoped
public class RequestIdFilter {

    static final String HEADER = RequestIds.HEADER;
    static final String MDC_KEY = RequestIds.KEY;

    /** Registered with a high priority, so it runs before all other filters. */
    void register(@Observes Filters filters) {
        filters.register(this::assignRequestId, 1000);
    }

    void assignRequestId(RoutingContext context) {
        String requestId = UUID.randomUUID().toString();
        context.put(MDC_KEY, requestId);
        context.response().putHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        context.addEndHandler(done -> MDC.remove(MDC_KEY));
        context.next();
    }
}
