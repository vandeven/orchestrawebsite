package org.example.website.accounts.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.example.website.platform.api.ServiceUnavailable;
import org.jboss.logging.Logger;

/**
 * The in-memory stores for tests and local work (ARC-6). They lose everything when the application
 * stops, so they are only used when {@code accounts.allow-in-memory-storage} is switched on, which
 * is the case for tests, development and the container smoke test, and never for a real deployment.
 * Until the Firestore stores exist, a production start without that switch fails.
 */
@ApplicationScoped
class InMemoryStorage implements UserRepository, SessionRepository {

    private static final Logger LOG = Logger.getLogger(InMemoryStorage.class);

    private final Map<UUID, UserRecord> users = new ConcurrentHashMap<>();
    private final Map<String, UUID> userIdByEmail = new ConcurrentHashMap<>();
    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    @Inject AccountsConfig config;

    private void checkAllowed() {
        if (!config.allowInMemoryStorage()) {
            LOG.error(
                    "No database is configured for accounts and in-memory storage is not allowed");
            throw new ServiceUnavailable("No account storage configured");
        }
    }

    // Users

    @Override
    public Optional<UserRecord> findByEmail(String normalizedEmail) {
        checkAllowed();
        UUID id = userIdByEmail.get(normalizedEmail);
        return id == null ? Optional.empty() : Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<UserRecord> findById(UUID id) {
        checkAllowed();
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public List<UserRecord> findAll() {
        checkAllowed();
        return new ArrayList<>(users.values());
    }

    @Override
    public boolean insertIfEmailFree(UserRecord user) {
        checkAllowed();
        if (userIdByEmail.putIfAbsent(user.email(), user.id()) != null) {
            return false;
        }
        users.put(user.id(), user);
        return true;
    }

    @Override
    public void update(UserRecord user) {
        checkAllowed();
        users.replace(user.id(), user);
    }

    @Override
    public void delete(UUID id) {
        checkAllowed();
        UserRecord removed = users.remove(id);
        if (removed != null) {
            userIdByEmail.remove(removed.email());
        }
    }

    // Sessions

    @Override
    public void save(SessionRecord session) {
        checkAllowed();
        sessions.put(session.tokenHash(), session);
    }

    @Override
    public Optional<SessionRecord> findByTokenHash(String tokenHash) {
        checkAllowed();
        return Optional.ofNullable(sessions.get(tokenHash));
    }

    @Override
    public void delete(String tokenHash) {
        checkAllowed();
        sessions.remove(tokenHash);
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        checkAllowed();
        sessions.values().removeIf(session -> session.userId().equals(userId));
    }
}
