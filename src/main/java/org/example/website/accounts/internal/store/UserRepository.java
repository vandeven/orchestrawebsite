package org.example.website.accounts.internal.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where users are stored (ARC-6). There is an in-memory implementation for tests and local work;
 * the Firestore implementation follows.
 */
public interface UserRepository {

    Optional<UserRecord> findByEmail(String normalizedEmail);

    Optional<UserRecord> findById(UUID id);

    List<UserRecord> findAll();

    /**
     * Stores a new user. False when the email address is taken: the check and the write are one
     * step.
     */
    boolean insertIfEmailFree(UserRecord user);

    void update(UserRecord user);

    void delete(UUID id);
}
