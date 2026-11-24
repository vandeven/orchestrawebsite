package org.example.website.content.api.images;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.api.access.DestroysData;

/** The image library (EDT-9 to EDT-17, HOS-14, HOS-22). Only admins and the master use it. */
public interface ImageLibrary {

    /** The largest original file, in bytes: 10 MB (EDT-10). */
    long MAX_BYTES = 10L * 1024 * 1024;

    /** The largest width and height in pixels (EDT, input rules). */
    int MAX_PIXELS = 6000;

    /** The longest alternative text (EDT-12). */
    int ALT_MAX = 250;

    /**
     * Gives a signed address to upload one image to. The type must be JPEG, PNG or WebP and the
     * size at most 10 MB; the signed address enforces both. At most so many requests per person per
     * hour (HRD-7).
     *
     * @throws org.example.website.platform.api.errors.InvalidInput for another type or a size that
     *     is zero or too large
     * @throws org.example.website.platform.api.errors.TooManyRequests when asked too often
     */
    UploadTicket requestUpload(String contentType, long sizeBytes, UserSummary actor);

    /**
     * Checks the uploaded file by its content (type, size and pixels) and adds it to the library. A
     * file that is not what it says is deleted again right away (HOS-14). The alternative text is
     * required (1 to 250 characters) unless the image is decorative.
     *
     * @throws org.example.website.platform.api.errors.InvalidInput when the file or the text is not
     *     acceptable
     */
    Image complete(UUID id, String contentType, String alt, boolean decorative, UserSummary actor);

    /** Every image, newest first, with the texts that use it (EDT-15, EDT-17). */
    List<ImageInfo> list();

    Optional<ImageInfo> find(UUID id);

    /**
     * Deletes the image from the library and from storage for good: there is no soft delete
     * (HOS-22). The caller has asked the admin to confirm (EDT-16).
     *
     * @throws org.example.website.platform.api.errors.ResourceNotFound when there is no such image
     */
    @DestroysData
    void delete(UUID id, UserSummary actor);
}
