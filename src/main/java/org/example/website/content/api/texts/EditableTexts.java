package org.example.website.content.api.texts;

import org.example.website.accounts.api.users.UserSummary;

/** The editable texts of the public pages (EDT). */
public interface EditableTexts {

    /**
     * The text, cleaned. A text that was never saved comes back empty, so the page shows its
     * placeholder (ABO-4).
     */
    EditableText get(TextKey key);

    /**
     * Cleans the HTML with the allowlist, stores it and records who did it in the audit trail
     * (without the content). Returns what was stored, which can differ from what was sent (EDT-6).
     * Throws {@code InvalidInput} when the cleaned text is over 200 KB.
     */
    EditableText save(TextKey key, String html, UserSummary actor);
}
