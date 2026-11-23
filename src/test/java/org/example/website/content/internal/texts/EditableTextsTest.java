package org.example.website.content.internal.texts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.audit.api.AuditAction;
import org.example.website.audit.api.AuditEntry;
import org.example.website.content.api.texts.EditableText;
import org.example.website.content.api.texts.TextKey;
import org.example.website.content.internal.config.ContentConfig;
import org.example.website.content.internal.store.memory.InMemoryTexts;
import org.example.website.platform.api.errors.InvalidInput;
import org.junit.jupiter.api.Test;

class EditableTextsTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private final ContentConfig config =
            new ContentConfig() {
                @Override
                public String imageBaseUrl() {
                    return "https://images.example.test/";
                }

                @Override
                public boolean allowInMemoryStorage() {
                    return true;
                }
            };
    private final List<AuditEntry> audited = new ArrayList<>();
    private final EditableTextsImpl texts = new EditableTextsImpl();
    private final UserSummary admin =
            new UserSummary(
                    UUID.randomUUID(), "admin@example.test", Role.ADMIN, NOW, null, true, null);

    EditableTextsTest() {
        texts.repository = new InMemoryTexts(config);
        texts.config = config;
        texts.audit = audited::add;
        texts.clock = Clock.fixed(NOW, ZoneOffset.UTC);
        texts.init();
    }

    @Test
    void aTextThatWasNeverSavedIsEmptyWithoutADate() {
        EditableText text = texts.get(TextKey.ABOUT);
        assertTrue(text.isEmpty());
        assertNull(text.changedAt());
    }

    @Test
    void aSavedTextIsCleanedStoredAndReturnedAsStored() {
        EditableText saved =
                texts.save(TextKey.ABOUT, "<p>Hello</p><script>alert(1)</script>", admin);
        assertEquals("<p>Hello</p>", saved.html());
        assertEquals(NOW, saved.changedAt());
        assertEquals("<p>Hello</p>", texts.get(TextKey.ABOUT).html());
    }

    @Test
    void theAuditTrailSaysWhoChangedWhichTextButNotWhatItSays() {
        texts.save(TextKey.AGENDA, "<p>Secret plans</p>", admin);
        assertEquals(1, audited.size());
        AuditEntry entry = audited.get(0);
        assertEquals(AuditAction.TEXT_CHANGE, entry.action());
        assertEquals("admin@example.test", entry.actor().email());
        assertEquals("agenda", entry.details().get("text"));
        assertFalse(entry.details().toString().contains("Secret"));
    }

    @Test
    void clearingATextIsAuditedAsAClear() {
        texts.save(TextKey.AUDITION, "<p>x</p>", admin);
        texts.save(TextKey.AUDITION, "   ", admin);
        assertEquals(AuditAction.TEXT_CLEAR, audited.get(1).action());
        assertTrue(texts.get(TextKey.AUDITION).isEmpty());
    }

    @Test
    void aTextOverTwoHundredKilobytesAfterCleaningIsRefusedAndNothingChanges() {
        texts.save(TextKey.ABOUT, "<p>old</p>", admin);
        String big = "<p>" + "a".repeat(EditableTextsImpl.MAX_CLEAN_BYTES) + "</p>";
        assertThrows(InvalidInput.class, () -> texts.save(TextKey.ABOUT, big, admin));
        assertEquals("<p>old</p>", texts.get(TextKey.ABOUT).html());
        assertEquals(1, audited.size());
    }

    @Test
    void theLimitIsOnTheCleanedTextNotTheRawOne() {
        // Under the raw limit, but all of it is removed by the cleaning
        String junk = "<script>" + "a".repeat(300 * 1024) + "</script><p>ok</p>";
        assertEquals("<p>ok</p>", texts.save(TextKey.ABOUT, junk, admin).html());
    }

    @Test
    void aRawTextOverOneMegabyteIsRefusedBeforeCleaning() {
        String huge = "a".repeat(EditableTextsImpl.MAX_RAW_BYTES + 1);
        assertThrows(InvalidInput.class, () -> texts.save(TextKey.ABOUT, huge, admin));
    }

    @Test
    void aStoredTextIsCleanedAgainWhenItIsShown() {
        // As if an older, looser version of the allowlist had stored it
        ((InMemoryTexts) texts.repository)
                .save(new EditableText(TextKey.ABOUT, "<p>x</p><script>y</script>", NOW));
        assertEquals("<p>x</p>", texts.get(TextKey.ABOUT).html());
    }

    @Test
    void onlyTheFourTextsExistAndAKeyCannotBeInvented() {
        assertEquals(4, TextKey.values().length);
        assertTrue(TextKey.fromKey("about").isPresent());
        assertTrue(TextKey.fromKey("../about").isEmpty());
        assertTrue(TextKey.fromKey("ABOUT").isEmpty());
        assertTrue(TextKey.fromKey(null).isEmpty());
    }
}
