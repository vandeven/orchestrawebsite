package org.example.website.content.internal.store.memory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.example.website.content.api.texts.EditableText;
import org.example.website.content.api.texts.TextKey;
import org.example.website.content.internal.config.ContentConfig;
import org.example.website.content.internal.store.TextRepository;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.jboss.logging.Logger;

/**
 * The texts in memory, lost when the application stops. Only used when {@code
 * content.allow-in-memory-storage} is switched on: tests, development and the container smoke test,
 * never a real deployment.
 */
@ApplicationScoped
@Typed(InMemoryTexts.class)
public class InMemoryTexts implements TextRepository {

    private static final Logger LOG = Logger.getLogger(InMemoryTexts.class);

    private final Map<TextKey, EditableText> texts = new ConcurrentHashMap<>();
    private final ContentConfig config;

    @Inject
    public InMemoryTexts(ContentConfig config) {
        this.config = config;
    }

    private void checkAllowed() {
        if (!config.allowInMemoryStorage()) {
            LOG.error("No database is configured for content and in-memory storage is not allowed");
            throw new ServiceUnavailable("No content storage configured");
        }
    }

    @Override
    public Optional<EditableText> find(TextKey key) {
        checkAllowed();
        return Optional.ofNullable(texts.get(key));
    }

    @Override
    public void save(EditableText text) {
        checkAllowed();
        texts.put(text.key(), text);
    }
}
