package org.example.website.accounts.internal.store;

import java.util.Optional;
import java.util.UUID;

/** Where recovery links are stored (ARC-6, USR-19). They expire by themselves. */
public interface RecoveryTokenRepository {

    void save(RecoveryTokenRecord token);

    Optional<RecoveryTokenRecord> findByHash(String tokenHash);

    void deleteByHash(String tokenHash);

    /** Removes every link of the user, so an older link stops working when a new one is made. */
    void deleteForUser(UUID userId);
}
