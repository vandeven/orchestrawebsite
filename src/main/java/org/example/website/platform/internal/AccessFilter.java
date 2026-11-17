package org.example.website.platform.internal;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.example.website.platform.api.Access;
import org.example.website.platform.api.AccessDenied;
import org.example.website.platform.api.AccessLevel;
import org.example.website.platform.api.VisitorAccess;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Enforces {@link Access} on every endpoint (ARC-7). An endpoint without it is refused. An
 * anonymous visitor who opens a page that needs a login is sent to the login page and returned
 * afterwards; a logged-in visitor without the right level gets the "access denied" page. The
 * decision is made on the server for every request.
 */
public class AccessFilter {

    private static final Logger LOG = Logger.getLogger(AccessFilter.class);

    @Inject VisitorAccess visitor;

    @ServerRequestFilter
    public void check(ContainerRequestContext request, ResourceInfo resource) {
        AccessLevel required = declaredLevel(resource);
        if (required == null) {
            LOG.errorf(
                    "Endpoint %s.%s has no @Access annotation, refusing the request",
                    resource.getResourceClass().getName(), resource.getResourceMethod().getName());
            throw new AccessDenied("Endpoint without @Access");
        }
        AccessLevel actual = visitor.currentLevel();
        if (actual.allows(required)) {
            return;
        }
        LOG.warnf("Access refused: a visitor at level %s opened a %s endpoint", actual, required);
        boolean anonymous = actual == AccessLevel.PUBLIC;
        String method = request.getMethod();
        if (anonymous && (method.equals("GET") || method.equals("HEAD"))) {
            // The address is a plain relative path: it is not resolved against the request, so a
            // forged Host header cannot end up in it.
            throw new WebApplicationException(
                    Response.status(Response.Status.SEE_OTHER)
                            .header("Location", loginAddress(request))
                            .header("Cache-Control", "no-store")
                            .build());
        }
        throw new AccessDenied("Level " + actual + " is below " + required);
    }

    /** The method's own annotation wins over the class annotation. */
    static AccessLevel declaredLevel(ResourceInfo resource) {
        Method method = resource.getResourceMethod();
        Access onMethod = method.getAnnotation(Access.class);
        if (onMethod != null) {
            return onMethod.value();
        }
        Access onClass = resource.getResourceClass().getAnnotation(Access.class);
        return onClass == null ? null : onClass.value();
    }

    /** A relative address, so it can never point to another site (no open redirect). */
    private static String loginAddress(ContainerRequestContext request) {
        String path = request.getUriInfo().getRequestUri().getRawPath();
        String query = request.getUriInfo().getRequestUri().getRawQuery();
        String wanted = query == null ? path : path + "?" + query;
        return "/login?return=" + URLEncoder.encode(wanted, StandardCharsets.UTF_8);
    }
}
