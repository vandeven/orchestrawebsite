package org.example.website.content.internal.images;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * The image storage (ARC-6): the public images bucket in Cloud Storage, or memory for tests. The
 * application never receives the upload itself: the browser sends it straight to storage (HOS-14).
 */
public interface ImageStorage {

    /** A signed address and the headers the upload must send exactly as given. */
    record SignedUpload(String url, Map<String, String> headers) {

        public SignedUpload {
            headers = Map.copyOf(headers);
        }
    }

    /**
     * A short-lived address to PUT one object to. The signature fixes the content type and a size
     * from 1 byte to {@code maxBytes}, so the limits cannot be bypassed from the browser.
     */
    SignedUpload signUpload(String objectName, String contentType, long maxBytes, Duration valid);

    /**
     * The object, or nothing when it does not exist. At most {@code limit} bytes are read; when the
     * object is larger, the result has {@code limit} bytes plus one.
     */
    Optional<byte[]> read(String objectName, int limit);

    /** Lets browsers keep the image for a year: a file never changes under its name (HOS-22). */
    void makeCacheable(String objectName);

    /** Removes the object. Not an error when it does not exist. */
    void delete(String objectName);
}
