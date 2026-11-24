package org.example.website.content.internal.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage of the image details (ARC-6). */
public interface ImageRepository {

    void save(ImageRecord image);

    Optional<ImageRecord> find(UUID id);

    /** Every image, newest first. */
    List<ImageRecord> findAll();

    /** Not an error when the image does not exist. */
    void delete(UUID id);
}
