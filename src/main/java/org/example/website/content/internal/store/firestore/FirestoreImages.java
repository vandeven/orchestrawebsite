package org.example.website.content.internal.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.example.website.content.internal.store.ImageRecord;
import org.example.website.content.internal.store.ImageRepository;
import org.example.website.platform.api.database.Database;

/**
 * The image details in Firestore (ARC-5). Collection {@code images}, one document per image, named
 * by its ID. The library is small (some hundreds at most), so it is read whole, newest first.
 */
@ApplicationScoped
@Typed(FirestoreImages.class)
public class FirestoreImages implements ImageRepository {

    static final String IMAGES = "images";
    private static final long SCHEMA_VERSION = 1;

    @Inject Database database;

    @Override
    public void save(ImageRecord image) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("schemaVersion", SCHEMA_VERSION);
        fields.put("objectName", image.objectName());
        fields.put("contentType", image.contentType());
        fields.put("width", (long) image.width());
        fields.put("height", (long) image.height());
        fields.put("sizeBytes", image.sizeBytes());
        fields.put("alt", image.alt());
        fields.put(
                "uploadedAt",
                Timestamp.ofTimeSecondsAndNanos(
                        image.uploadedAt().getEpochSecond(), image.uploadedAt().getNano()));
        database.await(
                database.firestore()
                        .collection(IMAGES)
                        .document(image.id().toString())
                        .set(fields));
    }

    @Override
    public Optional<ImageRecord> find(UUID id) {
        DocumentSnapshot document =
                database.await(
                        database.firestore().collection(IMAGES).document(id.toString()).get());
        return document.exists() ? Optional.of(record(document)) : Optional.empty();
    }

    @Override
    public List<ImageRecord> findAll() {
        return database
                .await(
                        database.firestore()
                                .collection(IMAGES)
                                .orderBy("uploadedAt", Query.Direction.DESCENDING)
                                .get())
                .getDocuments()
                .stream()
                .map(FirestoreImages::record)
                .toList();
    }

    @Override
    public void delete(UUID id) {
        database.await(database.firestore().collection(IMAGES).document(id.toString()).delete());
    }

    private static ImageRecord record(DocumentSnapshot document) {
        Timestamp uploaded = document.getTimestamp("uploadedAt");
        return new ImageRecord(
                UUID.fromString(document.getId()),
                document.getString("objectName"),
                document.getString("contentType"),
                Math.toIntExact(document.getLong("width")),
                Math.toIntExact(document.getLong("height")),
                document.getLong("sizeBytes"),
                document.getString("alt"),
                Instant.ofEpochSecond(uploaded.getSeconds(), uploaded.getNanos()));
    }
}
