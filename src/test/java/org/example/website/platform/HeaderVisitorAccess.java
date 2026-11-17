package org.example.website.platform;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.example.website.platform.api.AccessLevel;
import org.example.website.platform.api.VisitorAccess;

/** Test only: the visitor's level comes from the X-Test-Level header (anonymous without it). */
@Alternative
@Priority(1)
@ApplicationScoped
public class HeaderVisitorAccess implements VisitorAccess {

    @Inject CurrentVertxRequest currentRequest;

    @Override
    public AccessLevel currentLevel() {
        String value = currentRequest.getCurrent().request().getHeader("X-Test-Level");
        return value == null ? AccessLevel.PUBLIC : AccessLevel.valueOf(value);
    }
}
