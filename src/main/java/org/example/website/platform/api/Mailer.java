package org.example.website.platform.api;

/** Sends email (ARC-6, HOS-21). The real one uses Brevo; tests and local work use an outbox. */
public interface Mailer {

    /**
     * Sends the email.
     *
     * @throws ServiceUnavailable when the email service did not accept it. The message and the
     *     address are not in the error, so nothing personal ends up in a log.
     */
    void send(Email email);
}
