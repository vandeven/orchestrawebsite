package org.example.website.platform.internal.access;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import java.util.Set;
import org.example.website.platform.api.access.AccessLevel;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Tells search engines not to index pages that are not meant for them (plan: Search engines and
 * sharing): everything behind a login, the login and password pages, and every error page. {@code
 * robots.txt} says the same, but a page that is linked from elsewhere can still end up in a search
 * result without this header.
 */
public class NoIndexFilter {

    /** Public pages that are for visitors, not for search results. */
    private static final Set<String> PRIVATE_PUBLIC_PAGES =
            Set.of("/login", "/logout", "/forgot-password", "/reset-password");

    @ServerResponseFilter
    public void apply(
            ContainerRequestContext request,
            ContainerResponseContext response,
            ResourceInfo resource) {
        boolean secure =
                resource != null
                        && resource.getResourceMethod() != null
                        && AccessFilter.declaredLevel(resource) != AccessLevel.PUBLIC;
        boolean error = response.getStatus() >= 400;
        String path = request.getUriInfo().getPath();
        boolean noPublicIndex =
                PRIVATE_PUBLIC_PAGES.contains(path.startsWith("/") ? path : "/" + path);
        if (secure || error || noPublicIndex) {
            response.getHeaders().putSingle("X-Robots-Tag", "noindex, nofollow");
        }
    }
}
