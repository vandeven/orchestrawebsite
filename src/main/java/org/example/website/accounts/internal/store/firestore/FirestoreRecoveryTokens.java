package org.example.website.accounts.internal.store.firestore;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.example.website.accounts.internal.store.RecoveryTokenRecord;
import org.example.website.accounts.internal.store.RecoveryTokenRepository;
import org.example.website.platform.api.Database;

/**
 * Recovery links in Firestore (ARC-5, USR-19). Collection {@code recoveryTokens}, one document per
 * link, named by the hash of the token. Like sessions, the {@code expiresAt} moment is also used by
 * Firestore for cleanup, which can lag, so the service checks it when a link is used.
 */
@ApplicationScoped
@Typed(FirestoreRecoveryTokens.class)
public class FirestoreRecoveryTokens implements RecoveryTokenRepository {

    static final String TOKENS = "recoveryTokens";

    @Inject Database database;

    private CollectionReference tokens() {
        return database.firestore().collection(TOKENS);
    }

    @Override
    public void save(RecoveryTokenRecord token) {
        database.await(
                tokens().document(token.tokenHash())
                        .set(
                                Map.of(
                                        "schemaVersion", FirestoreFields.SCHEMA_VERSION,
                                        "userId", token.userId().toString(),
                                        "expiresAt",
                                                FirestoreFields.timestamp(token.expiresAt()))));
    }

    @Override
    public Optional<RecoveryTokenRecord> findByHash(String tokenHash) {
        DocumentSnapshot document = database.await(tokens().document(tokenHash).get());
        if (!document.exists()) {
            return Optional.empty();
        }
        return Optional.of(
                new RecoveryTokenRecord(
                        document.getId(),
                        FirestoreFields.uuid(document, "userId"),
                        FirestoreFields.instant(document, "expiresAt")));
    }

    @Override
    public void deleteByHash(String tokenHash) {
        database.await(tokens().document(tokenHash).delete());
    }

    @Override
    public void deleteForUser(UUID userId) {
        FirestoreFields.deleteWhereUser(database, tokens(), userId);
    }
}
