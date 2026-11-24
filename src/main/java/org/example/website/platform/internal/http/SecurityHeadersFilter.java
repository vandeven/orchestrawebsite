package org.example.website.platform.internal.http;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.example.website.platform.api.PlatformConfig;

/** Adds the security headers of HRD-6 to every response, including errors and static files. */
@ApplicationScoped
public class SecurityHeadersFilter {

    /** The policy without the images bucket, which is added from the settings. */
    static String contentSecurityPolicy(String imageOrigin, String fileOrigin) {
        return "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' "
                + imageOrigin
                + "; connect-src 'self' "
                + imageOrigin
                + " "
                + fileOrigin
                + "; media-src 'self' "
                + fileOrigin
                + "; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";
    }

    @Inject PlatformConfig config;

    private volatile String policy;

    static final String PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
                    + "microphone=(), payment=(), usb=()";

    void register(@Observes Filters filters) {
        filters.register(this::addSecurityHeaders, 900);
    }

    private String policy() {
        String current = policy;
        if (current == null) {
            current = contentSecurityPolicy(config.imageOrigin(), config.fileOrigin());
            policy = current;
        }
        return current;
    }

    void addSecurityHeaders(RoutingContext context) {
        // Set when the response headers are written, so no later code can leave them out.
        context.addHeadersEndHandler(done -> {
            MultiMap headers = context.response().headers();
            headers.set("Content-Security-Policy", policy());
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
