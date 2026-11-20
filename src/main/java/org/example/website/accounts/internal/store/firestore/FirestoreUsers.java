package org.example.website.accounts.internal.store.firestore;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.internal.store.UserRecord;
import org.example.website.accounts.internal.store.UserRepository;
import org.example.website.platform.api.database.Database;

/**
 * Users in Firestore (ARC-5, ARC-6). Collection {@code users}: one document per user, named by the
 * user's id. Collection {@code userEmails}: one document per address, named by a hash of the
 * address, written in the same transaction as the user. That second document is what keeps
 * addresses unique, because two people adding the same address at the same moment cannot both
 * create it.
 */
@ApplicationScoped
@Typed(FirestoreUsers.class)
public class FirestoreUsers implements UserRepository {

    static final String USERS = "users";
    static final String EMAILS = "userEmails";

    @Inject Database database;

    private CollectionReference users() {
        return database.firestore().collection(USERS);
    }

    private CollectionReference emails() {
        return database.firestore().collection(EMAILS);
    }

    @Override
    public Optional<UserRecord> findByEmail(String normalizedEmail) {
        var result = database.await(users().whereEqualTo("email", normalizedEmail).limit(1).get());
        return result.getDocuments().stream().findFirst().map(FirestoreUsers::record);
    }

    @Override
    public Optional<UserRecord> findById(UUID id) {
        DocumentSnapshot document = database.await(users().document(id.toString()).get());
        return document.exists() ? Optional.of(record(document)) : Optional.empty();
    }

    @Override
    public List<UserRecord> findAll() {
        return database.await(users().get()).getDocuments().stream()
                .map(FirestoreUsers::record)
                .toList();
    }

    @Override
    public boolean insertIfEmailFree(UserRecord user) {
        DocumentReference lock = emails().document(emailKey(user.email()));
        DocumentReference document = users().document(user.id().toString());
        return database.await(
                database.firestore()
                        .runTransaction(transaction -> {
                            if (transaction.get(lock).get().exists()) {
                                return false;
                            }
                            transaction.set(
                                    lock,
                                    Map.of(
                                            "userId",
                                            user.id().toString(),
                                            "schemaVersion",
                                            FirestoreFields.SCHEMA_VERSION));
                            transaction.set(document, fields(user));
                            return true;
                        }));
    }

    @Override
    public void update(UserRecord user) {
        DocumentReference document = users().document(user.id().toString());
        database.await(
                database.firestore()
                        .runTransaction(transaction -> {
                            if (transaction.get(document).get().exists()) {
                                transaction.set(document, fields(user));
                            }
                            return null;
                        }));
    }

    @Override
    public void delete(UUID id) {
        DocumentReference document = users().document(id.toString());
        database.await(
                database.firestore()
                        .runTransaction(transaction -> {
                            DocumentSnapshot current = transaction.get(document).get();
                            if (current.exists()) {
                                String email = current.getString("email");
                                transaction.delete(emails().document(emailKey(email)));
                                transaction.delete(document);
                            }
                            return null;
                        }));
    }

    /** The name of the document that holds an address: no address in a document name. */
    static String emailKey(String normalizedEmail) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java version", e);
        }
    }

    private static Map<String, Object> fields(UserRecord user) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("schemaVersion", FirestoreFields.SCHEMA_VERSION);
        fields.put("email", user.email());
        fields.put("role", user.role().name());
        fields.put("passwordHash", user.passwordHash());
        fields.put("createdAt", FirestoreFields.timestamp(user.createdAt()));
        fields.put("lastLoginAt", FirestoreFields.timestamp(user.lastLoginAt()));
        fields.put("recoveryEmailSentAt", FirestoreFields.timestamp(user.recoveryEmailSentAt()));
        return fields;
    }

    private static UserRecord record(DocumentSnapshot document) {
        return new UserRecord(
                UUID.fromString(document.getId()),
                document.getString("email"),
                Role.valueOf(document.getString("role")),
                document.getString("passwordHash"),
                FirestoreFields.instant(document, "createdAt"),
                FirestoreFields.instant(document, "lastLoginAt"),
                FirestoreFields.instant(document, "recoveryEmailSentAt"));
    }
}
