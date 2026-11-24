package org.example.website.content.api.images;

import java.util.List;
import org.example.website.content.api.texts.TextKey;

/** An image with the texts it is used in, for the library and for the delete confirmation. */
public record ImageInfo(Image image, List<TextKey> usedIn) {

    public ImageInfo {
        usedIn = List.copyOf(usedIn);
    }

    public boolean unused() {
        return usedIn.isEmpty();
    }
}
