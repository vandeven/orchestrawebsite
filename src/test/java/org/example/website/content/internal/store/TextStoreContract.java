package org.example.website.content.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.example.website.content.api.texts.EditableText;
import org.example.website.content.api.texts.TextKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** One set of cases for every text store: the in-memory and the Firestore one (ARC-6). */
public abstract class TextStoreContract {

    protected abstract TextRepository texts();

    /** Removes every stored text, so each case starts from nothing. */
    protected abstract void reset();

    @BeforeEach
    void startClean() {
        reset();
    }

    @Test
    void aTextThatWasNeverSavedIsNotFound() {
        assertTrue(texts().find(TextKey.AGENDA).isEmpty());
    }

    @Test
    void aSavedTextComesBack() {
        Instant moment = Instant.parse("2026-11-23T10:15:30.123456Z");
        texts().save(new EditableText(TextKey.ABOUT, "<p>Hello</p>", moment));
        EditableText found = texts().find(TextKey.ABOUT).orElseThrow();
        assertEquals("<p>Hello</p>", found.html());
        assertEquals(moment, found.changedAt());
    }

    @Test
    void savingReplacesTheEarlierTextAndKeepsNoHistory() {
        texts().save(
                        new EditableText(
                                TextKey.AUDITION, "one", Instant.parse("2026-01-01T00:00:00Z")));
        texts().save(
                        new EditableText(
                                TextKey.AUDITION, "two", Instant.parse("2026-01-02T00:00:00Z")));
        assertEquals("two", texts().find(TextKey.AUDITION).orElseThrow().html());
    }

    @Test
    void textsDoNotInfluenceEachOther() {
        texts().save(
                        new EditableText(
                                TextKey.BECOME_A_MEMBER,
                                "a",
                                Instant.parse("2026-01-01T00:00:00Z")));
        assertTrue(texts().find(TextKey.AGENDA).isEmpty());
    }

    @Test
    void aClearedTextIsStoredAsEmpty() {
        texts().save(new EditableText(TextKey.AGENDA, "x", Instant.parse("2026-01-01T00:00:00Z")));
        texts().save(new EditableText(TextKey.AGENDA, "", Instant.parse("2026-01-02T00:00:00Z")));
        EditableText found = texts().find(TextKey.AGENDA).orElseThrow();
        assertTrue(found.isEmpty());
    }
}
