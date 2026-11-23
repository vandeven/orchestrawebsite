package org.example.website.content.internal.texts;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.audit.api.AuditAction;
import org.example.website.audit.api.AuditEntry;
import org.example.website.audit.api.AuditTrail;
import org.example.website.content.api.texts.EditableText;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.example.website.content.internal.config.ContentConfig;
import org.example.website.content.internal.html.HtmlCleaner;
import org.example.website.content.internal.store.TextRepository;
import org.example.website.platform.api.errors.InvalidInput;
import org.jboss.logging.Logger;

/** The editable texts (EDT): cleaned when saved and again when shown (HRD-5). */
@ApplicationScoped
public class EditableTextsImpl implements EditableTexts {

    /** At most 200 KB after cleaning. */
    static final int MAX_CLEAN_BYTES = 200 * 1024;

    /** What is read from the request is limited first, so cleaning cannot be used to burn time. */
    static final int MAX_RAW_BYTES = 1024 * 1024;

    private static final Logger LOG = Logger.getLogger(EditableTextsImpl.class);

    @Inject TextRepository repository;
    @Inject ContentConfig config;
    @Inject AuditTrail audit;
    @Inject Clock clock;

    private HtmlCleaner cleaner;

    @PostConstruct
    void init() {
        cleaner = new HtmlCleaner(config.imageBaseUrl());
    }

    @Override
    public EditableText get(TextKey key) {
        return repository
                .find(key)
                // Cleaned again when shown: a text stored by an older, looser version of the
                // allowlist can never reach a visitor as it was (HRD-5)
                .map(text -> new EditableText(key, cleaner.clean(text.html()), text.changedAt()))
                .orElse(new EditableText(key, "", null));
    }

    @Override
    public EditableText save(TextKey key, String html, UserSummary actor) {
        String raw = html == null ? "" : html;
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_RAW_BYTES) {
            throw new InvalidInput("The text is too long");
        }
        String clean = cleaner.clean(raw).strip();
        if (clean.getBytes(StandardCharsets.UTF_8).length > MAX_CLEAN_BYTES) {
            throw new InvalidInput("The text is too long");
        }
        EditableText saved = new EditableText(key, clean, clock.instant());
        repository.save(saved);
        LOG.infof("Text %s saved (%d characters)", key.key(), clean.length());
        audit.record(
                new AuditEntry(
                        clean.isBlank() ? AuditAction.TEXT_CLEAR : AuditAction.TEXT_CHANGE,
                        new AuditEntry.Person(actor.id(), actor.email(), actor.role().name()),
                        null,
                        AuditEntry.Result.SUCCESS,
                        // Which text, never its content (LGG-10)
                        Map.of("text", key.key())));
        return saved;
    }
}
