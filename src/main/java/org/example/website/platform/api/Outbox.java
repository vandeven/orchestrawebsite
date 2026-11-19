package org.example.website.platform.api;

import java.util.List;

/**
 * The mail that was "sent" to the in-memory outbox instead of a real email service (ARC-6). Used by
 * tests and by the development page. It is empty when the real email service is configured.
 */
public interface Outbox {

    /** Everything sent so far, oldest first. */
    List<Email> sent();

    void clear();

    /** Makes the next sends fail, like an email service that is down (tests). */
    void failSends(boolean failing);
}
