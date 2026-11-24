package org.example.website.content.internal.images;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The images in memory, for tests and local work. {@link #put} stands in for the browser's upload.
 */
@ApplicationScoped
@Typed(InMemoryImageStorage.class)
public class InMemoryImageStorage implements ImageStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cacheable = new ConcurrentHashMap<>();

    @Override
    public SignedUpload signUpload(
            String objectName, String contentType, long maxBytes, Duration valid) {
        return new SignedUpload(
                "https://upload.example.test/" + objectName + "?signed=" + valid.toMinutes(),
                Map.of(
                        "Content-Type",
                        contentType,
                        "x-goog-content-length-range",
                        "1," + maxBytes));
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
    public void makeCacheable(String objectName) {
        cacheable.put(objectName, true);
    }

    @Override
    public void delete(String objectName) {
        objects.remove(objectName);
        cacheable.remove(objectName);
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
    public boolean isCacheable(String objectName) {
        return cacheable.containsKey(objectName);
    }

    /** For tests. */
    public void clear() {
        objects.clear();
        cacheable.clear();
    }
}
