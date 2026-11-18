package org.example.website.accounts.internal;

import java.util.Optional;
import java.util.UUID;

/** Where sessions are stored (ARC-6, LOG-11). */
interface SessionRepository {

    void save(SessionRecord session);

    Optional<SessionRecord> findByTokenHash(String tokenHash);

    void delete(String tokenHash);

    void deleteAllForUser(UUID userId);
}
