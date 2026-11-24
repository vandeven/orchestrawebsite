package org.example.website.content.api.images;

import java.util.Map;
import java.util.UUID;

/**
 * What the browser needs to upload one image straight to the image storage: the signed address, the
 * headers it must send exactly as given, and the ID to report back when it is done (HOS-14).
 */
public record UploadTicket(UUID id, String uploadUrl, Map<String, String> headers) {

    public UploadTicket {
        headers = Map.copyOf(headers);
    }
}
