package org.example.website.platform.internal;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.website.platform.api.AccessLevel;
import org.example.website.platform.api.VisitorAccess;

/** Used until the accounts component provides a real implementation: nobody is logged in. */
@ApplicationScoped
@DefaultBean
class AnonymousVisitorAccess implements VisitorAccess {

    @Override
    public AccessLevel currentLevel() {
        return AccessLevel.PUBLIC;
    }
}
