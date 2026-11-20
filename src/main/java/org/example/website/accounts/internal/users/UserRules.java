package org.example.website.accounts.internal.users;

import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserManagement.Actions;

/**
 * Who may do what to whom (USR-9 to USR-13). One place, used by the server to refuse and by the
 * overview to decide which buttons to show, so the two cannot disagree.
 */
final class UserRules {

    private UserRules() {}

    /** What a user with role {@code actor} may do with a user with role {@code target}. */
    static Actions actions(Role actor, Role target) {
        boolean manager = actor == Role.ADMIN || actor == Role.MASTER;
        boolean master = actor == Role.MASTER;
        return new Actions(
                manager && target != Role.MASTER, // USR-6, USR-16
                // USR-9, USR-11, USR-12, USR-13
                (actor == Role.ADMIN && target == Role.MEMBER)
                        || (master && (target == Role.MEMBER || target == Role.ADMIN)),
                manager && target == Role.MEMBER, // USR-10
                master && target == Role.ADMIN); // USR-12
    }
}
