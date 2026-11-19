package org.example.website.accounts.api;

/** What a logged-in user can do with their own account (ACC-1 to ACC-9). */
public interface AccountService {

    /**
     * Changes the password of the user who owns the session (ACC-3 to ACC-7). All other sessions of
     * the user end, and the current device gets a new session token that lasts as long as the old
     * one would have (the day of a session is never extended). The user gets an email about it. A
     * wrong current password counts toward the same limits as failed logins (ACC-4).
     *
     * @param sessionToken the session of the user who changes the password
     * @param clientAddress the visitor's address, for the limits; null when it is not known
     * @return the new session token for this device
     * @throws org.example.website.platform.api.AccessDenied when the session is not valid or
     *     belongs to the master (ACC-8)
     * @throws CurrentPasswordIncorrect when the current password is wrong
     * @throws org.example.website.platform.api.InvalidInput when the new password breaks the rules
     *     or equals the current one
     * @throws org.example.website.platform.api.TooManyRequests after too many wrong attempts
     */
    String changePassword(
            String sessionToken, String currentPassword, String newPassword, String clientAddress);
}
