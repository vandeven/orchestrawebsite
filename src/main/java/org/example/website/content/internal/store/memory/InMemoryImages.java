package org.example.website.content.internal.store.memory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.example.website.content.internal.config.ContentConfig;
import org.example.website.content.internal.store.ImageRecord;
import org.example.website.content.internal.store.ImageRepository;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.jboss.logging.Logger;

/** The image details in memory, for tests and local work (ARC-6). */
@ApplicationScoped
@Typed(InMemoryImages.class)
public class InMemoryImages implements ImageRepository {

    private static final Logger LOG = Logger.getLogger(InMemoryImages.class);

    private final Map<UUID, ImageRecord> images = new ConcurrentHashMap<>();
    private final ContentConfig config;

    @Inject
    public InMemoryImages(ContentConfig config) {
        this.config = config;
    }

    private void checkAllowed() {
        if (!config.allowInMemoryStorage()) {
            LOG.error("No database is configured for content and in-memory storage is not allowed");
            throw new ServiceUnavailable("No content storage configured");
        }
    }

    @Override
    public void save(ImageRecord image) {
        checkAllowed();
        images.put(image.id(), image);
    }

    @Override
    public Optional<ImageRecord> find(UUID id) {
        checkAllowed();
        return Optional.ofNullable(images.get(id));
    }

    @Override
    public List<ImageRecord> findAll() {
        checkAllowed();
        return images.values().stream()
                .sorted(Comparator.comparing(ImageRecord::uploadedAt).reversed())
                .toList();
    }

    @Override
    public void delete(UUID id) {
        checkAllowed();
        images.remove(id);
    }

    /** For tests. */
    public void clear() {
        images.clear();
    }
}
