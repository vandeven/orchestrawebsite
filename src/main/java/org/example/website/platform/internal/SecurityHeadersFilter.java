package org.example.website.platform.internal;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/** Adds the security headers of HRD-6 to every response, including errors and static files. */
@ApplicationScoped
public class SecurityHeadersFilter {

    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; "
                    + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    static final String PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
                    + "microphone=(), payment=(), usb=()";

    void register(@Observes Filters filters) {
        filters.register(this::addSecurityHeaders, 900);
    }

    void addSecurityHeaders(RoutingContext context) {
        // Set when the response headers are written, so no later code can leave them out.
        context.addHeadersEndHandler(done -> {
            MultiMap headers = context.response().headers();
            headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
            headers.set("Strict-Transport-Security", "max-age=31536000");
            headers.set("X-Content-Type-Options", "nosniff");
            if (!headers.contains("Referrer-Policy")) {
                headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
            }
            headers.set("Permissions-Policy", PERMISSIONS_POLICY);
            headers.set("Cross-Origin-Opener-Policy", "same-origin");
            headers.set("Cross-Origin-Resource-Policy", "same-origin");
        });
        context.next();
    }
}
