package org.example.website.platform.api;

/**
 * Tells the access filter what the current visitor may open. The {@code accounts} component
 * provides the real implementation (from the session); until it exists every visitor is anonymous.
 * It is an interface in {@code platform} so that {@code platform} depends on no other component.
 */
public interface VisitorAccess {

    /** The highest level the current visitor may open: PUBLIC for anonymous visitors. */
    AccessLevel currentLevel();
}
