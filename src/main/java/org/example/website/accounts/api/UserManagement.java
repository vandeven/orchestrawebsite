package org.example.website.accounts.api;

import java.util.List;
import java.util.UUID;
import org.example.website.platform.api.access.DestroysData;

/**
 * What admins and the master can do with users (USR-1 to USR-13, USR-22 to USR-24). Every method
 * takes the ID of the user who acts and checks that user's role first, on the server, whatever the
 * interface showed. Every call is written to the audit trail, also when it is refused.
 *
 * <p>The rules (USR-9 to USR-13): admins can remove members and make members admins; only the
 * master can remove admins or take away admin rights; nobody can touch the master account.
 */
public interface UserManagement {

    /** The columns the overview can be sorted on (USR-8). */
    enum Sort {
        EMAIL,
        ROLE,
        ADDED,
        LAST_LOGIN,
        RECOVERY_EMAIL
    }

    /** How the overview is sorted and filtered; a null role shows all roles. */
    record Query(Sort sort, boolean ascending, Role role) {}

    /**
     * What the acting user may do with one user. The interface only shows these; the server checks
     * again.
     */
    record Actions(boolean resendRecovery, boolean remove, boolean makeAdmin, boolean makeMember) {}

    record UserRow(UserSummary user, Actions actions) {}

    /** The result of adding a user: the account exists; the email may have failed (USR-24). */
    record AddResult(UserSummary user, boolean recoveryEmailSent) {}

    /**
     * All users with what the actor may do with each (USR-7, USR-8).
     *
     * @throws org.example.website.platform.api.errors.AccessDenied unless the actor is an admin or
     *     the master
     */
    List<UserRow> list(UUID actorId, Query query);

    /** One user, for the confirmation pages. Same access rule as {@link #list}. */
    UserRow find(UUID actorId, UUID targetId);

    /**
     * Adds a member and sends the recovery email (USR-2 to USR-5). When the email fails the account
     * still exists and the result says so, so the admin can send it again.
     *
     * @throws org.example.website.platform.api.errors.InvalidInput for an invalid address
     * @throws org.example.website.platform.api.errors.Conflict when the address already has an
     *     account
     */
    AddResult add(UUID actorId, String email);

    /**
     * Sends the recovery email again (USR-6). Not for the master account.
     *
     * @throws org.example.website.platform.api.errors.ServiceUnavailable when the email service
     *     failed
     */
    void resendRecovery(UUID actorId, UUID targetId);

    /** Removes a user and all their sessions (USR-9, USR-12). */
    @DestroysData
    void remove(UUID actorId, UUID targetId);

    /** Makes a member an admin (USR-10). */
    @DestroysData
    void makeAdmin(UUID actorId, UUID targetId);

    /** Takes admin rights away from an admin: the master only (USR-12). */
    @DestroysData
    void makeMember(UUID actorId, UUID targetId);
}
