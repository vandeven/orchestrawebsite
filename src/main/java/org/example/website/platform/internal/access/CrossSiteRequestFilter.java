package org.example.website.platform.internal.access;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.example.website.platform.api.PlatformConfig;
import org.example.website.platform.api.errors.AccessDenied;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Refuses state-changing requests that do not come from this website (LOG-14, ARC-10). Browsers
 * always tell where a request comes from: a {@code POST} from a page of this website carries our
 * own address in {@code Origin} (or {@code Sec-Fetch-Site: same-origin}), while a form on another
 * site cannot fake that. A request that says nothing at all is not from a current browser and is
 * refused as well. This is the first layer; a token in the form is added with the first logged-in
 * forms.
 */
public class CrossSiteRequestFilter {

    private static final Logger LOG = Logger.getLogger(CrossSiteRequestFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Inject PlatformConfig config;

    @ServerRequestFilter(preMatching = true)
    public void check(ContainerRequestContext request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return;
        }
        String origin = request.getHeaderString("Origin");
        String site = request.getHeaderString("Sec-Fetch-Site");
        boolean allowed;
        if (origin != null) {
            allowed = origin.equalsIgnoreCase(ownOrigin());
        } else {
            allowed = site != null && site.equalsIgnoreCase("same-origin");
        }
        if (!allowed) {
            // The values come from the visitor, so they are not written to the log (LGG-18)
            LOG.warnf(
                    "Refused a %s request that does not come from this website (origin sent: %s)",
                    request.getMethod().toUpperCase(Locale.ROOT), origin != null);
            throw new AccessDenied("Request did not come from this website");
        }
    }

    /** The address of this website as a browser writes it in the Origin header. */
    private String ownOrigin() {
        URI base = URI.create(config.publicBaseUrl());
        String port = base.getPort() == -1 ? "" : ":" + base.getPort();
        return base.getScheme() + "://" + base.getHost() + port;
    }
}
