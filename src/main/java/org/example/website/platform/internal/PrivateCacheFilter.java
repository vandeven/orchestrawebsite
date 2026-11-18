package org.example.website.platform.internal;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.example.website.platform.api.AccessLevel;
import org.example.website.platform.api.VisitorAccess;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Responses for logged-in visitors are never stored by a shared cache or kept by the browser
 * (LOG-12, plan: Layout and navigation). The page is the same for everyone but the menu is not, so
 * the cookie also decides which version a cache may hand out.
 */
public class PrivateCacheFilter {

    @Inject VisitorAccess visitor;

    @ServerResponseFilter
    public void apply(ContainerRequestContext request, ContainerResponseContext response) {
        if (visitor.currentLevel() != AccessLevel.PUBLIC) {
            response.getHeaders().putSingle("Cache-Control", "private, no-store");
            response.getHeaders().putSingle("Vary", "Cookie");
        }
    }
}
