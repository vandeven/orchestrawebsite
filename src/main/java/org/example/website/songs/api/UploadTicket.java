package org.example.website.songs.api;

import java.util.Map;
import java.util.UUID;

/** What the browser needs to upload one file straight to storage (HOS-14). */
public record UploadTicket(UUID fileId, String uploadUrl, Map<String, String> headers) {

    public UploadTicket {
        headers = Map.copyOf(headers);
    }
}
