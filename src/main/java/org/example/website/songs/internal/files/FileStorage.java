package org.example.website.songs.internal.files;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * The private files bucket (ARC-6, HOS-11): Cloud Storage, or memory for tests. Browsers upload and
 * download straight from storage with short-lived signed addresses; the application never handles a
 * file's content except to check it once after an upload (HOS-13, HOS-14).
 */
public interface FileStorage {

    /** A signed address and the headers the upload must send exactly as given. */
    record SignedUpload(String url, Map<String, String> headers) {

        public SignedUpload {
            headers = Map.copyOf(headers);
        }
    }

    /**
     * A short-lived address to PUT one object to. The signature fixes the content type and a size
     * from 1 byte to {@code maxBytes}.
     */
    SignedUpload signUpload(String objectName, String contentType, long maxBytes, Duration valid);

    /**
     * A short-lived address to GET one object from. The browser is told to treat it as {@code
     * contentType} and with the given {@code Content-Disposition} (see {@link ContentDisposition}).
     */
    String signDownload(
            String objectName, String contentType, String contentDisposition, Duration valid);

    /**
     * The object, or nothing when it does not exist. At most {@code limit} bytes are read; when the
     * object is larger the result has {@code limit} plus one bytes.
     */
    Optional<byte[]> read(String objectName, int limit);

    /** Removes the object. Not an error when it does not exist. */
    void delete(String objectName);
}
