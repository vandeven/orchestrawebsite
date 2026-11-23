package org.example.website.content.internal.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.example.website.content.api.texts.EditableText;
import org.example.website.content.api.texts.TextKey;
import org.example.website.content.internal.store.TextRepository;
import org.example.website.platform.api.database.Database;

/**
 * The editable texts in Firestore (ARC-5). Collection {@code texts}, one document per text, named
 * by its key. Fields: {@code html} (cleaned), {@code changedAt}, {@code schemaVersion}.
 */
@ApplicationScoped
@Typed(FirestoreTexts.class)
public class FirestoreTexts implements TextRepository {

    static final String TEXTS = "texts";
    static final long SCHEMA_VERSION = 1;

    @Inject Database database;

    @Override
    public Optional<EditableText> find(TextKey key) {
        DocumentSnapshot document =
                database.await(database.firestore().collection(TEXTS).document(key.key()).get());
        if (!document.exists()) {
            return Optional.empty();
        }
        Timestamp changed = document.getTimestamp("changedAt");
        return Optional.of(
                new EditableText(
                        key,
                        document.getString("html"),
                        changed == null
                                ? null
                                : Instant.ofEpochSecond(changed.getSeconds(), changed.getNanos())));
    }

    @Override
    public void save(EditableText text) {
        Instant changed = text.changedAt();
        database.await(
                database.firestore()
                        .collection(TEXTS)
                        .document(text.key().key())
                        .set(
                                Map.of(
                                        "schemaVersion", SCHEMA_VERSION,
                                        "html", text.html(),
                                        "changedAt",
                                                Timestamp.ofTimeSecondsAndNanos(
                                                        changed.getEpochSecond(),
                                                        changed.getNano()))));
    }
}
