package org.example.website.platform.api.access;

/**
 * The access levels of a page or endpoint (plan: Access control). The order matters: a visitor may
 * open everything up to and including their own level, so an admin can open member pages and the
 * master can open admin pages.
 */
public enum AccessLevel {
    /** Anyone, including anonymous visitors. */
    PUBLIC,
    /** Logged-in members, admins and the master. */
    MEMBER,
    /** Logged-in admins and the master. */
    ADMIN,
    /** The master only. */
    MASTER;

    /** True when a visitor at this level may open something that needs {@code required}. */
    public boolean allows(AccessLevel required) {
        return ordinal() >= required.ordinal();
    }
}
