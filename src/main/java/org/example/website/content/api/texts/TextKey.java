package org.example.website.content.api.texts;

import java.util.Optional;

/**
 * The editable texts that exist. Admins can change them but not add new ones (EDT-2). A new one is
 * added here by a developer.
 */
public enum TextKey {
    ABOUT("about", "About page text", "About"),
    BECOME_A_MEMBER("become-a-member", "Become a member page text", "Become a member"),
    AUDITION("audition", "Audition text", "Become a member (audition section)"),
    AGENDA("agenda", "Agenda text", "Agenda");

    private final String key;
    private final String title;
    private final String shownOn;

    TextKey(String key, String title, String shownOn) {
        this.key = key;
        this.title = title;
        this.shownOn = shownOn;
    }

    /** The name in the database and in the address of the editor, for example {@code about}. */
    public String key() {
        return key;
    }

    /** The name in the list of texts. */
    public String title() {
        return title;
    }

    /** The page the text appears on, for the list of texts. */
    public String shownOn() {
        return shownOn;
    }

    /** The text with that key, or nothing: a key that is not on the list is never accepted. */
    public static Optional<TextKey> fromKey(String key) {
        for (TextKey candidate : values()) {
            if (candidate.key.equals(key)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
