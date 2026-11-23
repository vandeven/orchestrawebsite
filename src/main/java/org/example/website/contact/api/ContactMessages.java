package org.example.website.contact.api;

import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.example.website.platform.api.errors.TooManyRequests;

/** The contact form (BAM-13 to BAM-17). Messages are only emailed, never stored (BAM-17). */
public interface ContactMessages {

    /** The longest subject and message, and the longest sender address. */
    int SUBJECT_MAX = 200;

    int MESSAGE_MAX = 5000;

    /**
     * Checks the message and emails it to every admin and the master, with the sender's address as
     * the reply address. Nothing is stored. The text is sent as plain text, never as markup.
     *
     * @param clientAddress the visitor's network address for the rate limit, or null when unknown
     * @throws InvalidInput when a rule is broken (the message says which field, never more)
     * @throws TooManyRequests when the visitor or the whole website sent too many messages
     * @throws ServiceUnavailable when no admin could be emailed
     */
    void send(String senderEmail, String subject, String message, String clientAddress);

    /**
     * Checks the rules only, without sending or counting. Returns the problem per field, or an
     * empty map when the message is fine.
     */
    java.util.Map<String, String> problems(String senderEmail, String subject, String message);
}
