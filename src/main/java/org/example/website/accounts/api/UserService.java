package org.example.website.accounts.api;

import java.util.Optional;
import java.util.UUID;

/** Creating users and setting passwords (user management, USR-2 to USR-4 and USR-21). */
public interface UserService {

    /**
     * Creates a user without a password: nobody can log in with the account until a password is set
     * (USR-4).
     *
     * @throws org.example.website.platform.api.errors.InvalidInput when the address is not valid
     * @throws org.example.website.platform.api.errors.Conflict when the address already has an
     *     account
     */
    UserSummary createUser(String email, Role role);

    /**
     * Sets a new password and ends all sessions of the user (USR-21).
     *
     * @throws org.example.website.platform.api.errors.InvalidInput when the password breaks the
     *     rules
     * @throws org.example.website.platform.api.errors.ResourceNotFound when the user does not exist
     */
    void setPassword(UUID userId, String newPassword);

    Optional<UserSummary> find(UUID userId);
}
