package org.example.website.content.internal.store;

import java.util.Optional;
import org.example.website.content.api.texts.EditableText;
import org.example.website.content.api.texts.TextKey;

/** Storage of the editable texts. No history is kept: saving replaces the text (EDT). */
public interface TextRepository {

    Optional<EditableText> find(TextKey key);

    void save(EditableText text);
}
