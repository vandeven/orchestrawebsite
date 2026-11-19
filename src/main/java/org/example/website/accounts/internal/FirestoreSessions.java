package org.example.website.accounts.internal;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.example.website.platform.api.Database;

/**
 * Sessions in Firestore (ARC-5, LOG-11). Collection {@code sessions}, one document per session,
 * named by the hash of the token. {@code expiresAt} is also what Firestore uses to clean up old
 * sessions by itself, which can take a day, so a session is never trusted on that alone: the
 * login service checks the moment on every request (LOG-10).
 */
@ApplicationScoped
@Typed(FirestoreSessions.class)
class FirestoreSessions implements SessionRepository {

    static final String SESSIONS = "sessions";

    @Inject Database database;

    private CollectionReference sessions() {
        return database.firestore().collection(SESSIONS);
    }

    @Override
    public void save(SessionRecord session) {
        database.await(
                sessions()
                        .document(session.tokenHash())
                        .set(
                                Map.of(
                                        "schemaVersion", FirestoreFields.SCHEMA_VERSION,
                                        "userId", session.userId().toString(),
                                        "expiresAt",
                                                FirestoreFields.timestamp(session.expiresAt()))));
    }

    @Override
    public Optional<SessionRecord> findByTokenHash(String tokenHash) {
        DocumentSnapshot document = database.await(sessions().document(tokenHash).get());
        if (!document.exists()) {
            return Optional.empty();
        }
        return Optional.of(
                new SessionRecord(
                        document.getId(),
                        FirestoreFields.uuid(document, "userId"),
                        FirestoreFields.instant(document, "expiresAt")));
    }

    @Override
    public void delete(String tokenHash) {
        database.await(sessions().document(tokenHash).delete());
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        FirestoreFields.deleteWhereUser(database, sessions(), userId);
    }
}
