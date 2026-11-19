package org.example.website.accounts.api;

import org.example.website.platform.api.AccessLevel;

/** The role of a user (see user management). */
public enum Role {
    MEMBER(AccessLevel.MEMBER),
    ADMIN(AccessLevel.ADMIN),
    MASTER(AccessLevel.MASTER);

    private final AccessLevel level;

    Role(AccessLevel level) {
        this.level = level;
    }

    /** The highest access level this role may open. */
    public AccessLevel level() {
        return level;
    }
}
