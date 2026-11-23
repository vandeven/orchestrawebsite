package org.example.website.content.api.texts;

import java.time.Instant;

/**
 * One editable text as it is shown: cleaned HTML and the moment it was last changed. {@code html}
 * is empty for a text that was never saved or was cleared; {@code changedAt} is then null when it
 * was never saved.
 */
public record EditableText(TextKey key, String html, Instant changedAt) {

    public EditableText {
        html = html == null ? "" : html;
    }

    public boolean isEmpty() {
        return html.isBlank();
    }
}
