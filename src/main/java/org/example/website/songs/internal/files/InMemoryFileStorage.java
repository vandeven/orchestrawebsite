package org.example.website.songs.internal.files;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The files in memory, for tests and local work. {@link #put} stands in for the browser's upload.
 */
@ApplicationScoped
@Typed(InMemoryFileStorage.class)
public class InMemoryFileStorage implements FileStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public SignedUpload signUpload(
            String objectName, String contentType, long maxBytes, Duration valid) {
        return new SignedUpload(
                "https://files.example.test/upload/" + objectName + "?valid=" + valid.toMinutes(),
                Map.of(
                        "Content-Type",
                        contentType,
                        "x-goog-content-length-range",
                        "1," + maxBytes));
    }

    @Override
    public String signDownload(
            String objectName, String contentType, String contentDisposition, Duration valid) {
        return "https://files.example.test/download/"
                + objectName
                + "?type="
                + contentType
                + "&disposition="
                + contentDisposition.replace(' ', '+')
                + "&valid="
                + valid.toMinutes();
    }

    @Override
    public Optional<byte[]> read(String objectName, int limit) {
        byte[] data = objects.get(objectName);
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(data.length > limit ? Arrays.copyOf(data, limit + 1) : data);
    }

    @Override
    public void delete(String objectName) {
        objects.remove(objectName);
    }

    /** For tests: what the browser would have uploaded. */
    public void put(String objectName, byte[] data) {
        objects.put(objectName, data);
    }

    /** For tests. */
    public boolean exists(String objectName) {
        return objects.containsKey(objectName);
    }

    /** For tests. */
    public int count() {
        return objects.size();
    }

    /** For tests. */
    public void clear() {
        objects.clear();
    }
}
