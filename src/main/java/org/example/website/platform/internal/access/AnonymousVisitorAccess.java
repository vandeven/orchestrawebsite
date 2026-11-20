package org.example.website.platform.internal.access;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.access.VisitorAccess;

/** Used until the accounts component provides a real implementation: nobody is logged in. */
@ApplicationScoped
@DefaultBean
class AnonymousVisitorAccess implements VisitorAccess {

    @Override
    public AccessLevel currentLevel() {
        return AccessLevel.PUBLIC;
    }
}
