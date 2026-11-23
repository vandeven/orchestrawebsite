package org.example.website.content.api.texts;

import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.api.access.DestroysData;

/** The editable texts of the public pages (EDT). */
public interface EditableTexts {

    /**
     * The text, cleaned. A text that was never saved comes back empty, so the page shows its
     * placeholder (ABO-4).
     */
    EditableText get(TextKey key);

    /**
     * The HTML as it would be stored: cleaned with the allowlist. Used for the preview, and to see
     * whether a text would be empty (EDT-7, EDT-18).
     */
    String clean(String html);

    /**
     * Cleans the HTML with the allowlist, stores it and records who did it in the audit trail
     * (without the content). Returns what was stored, which can differ from what was sent (EDT-6).
     * Throws {@code InvalidInput} when the cleaned text is over 200 KB, and when it is empty:
     * clearing a text is {@link #clear} and needs a confirmation.
     */
    EditableText save(TextKey key, String html, UserSummary actor);

    /** Empties the text. No earlier version is kept (EDT-18). */
    @DestroysData
    EditableText clear(TextKey key, UserSummary actor);
}
