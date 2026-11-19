package org.example.website.accounts.api;

import java.time.Duration;
import java.util.UUID;

/** Password recovery and the first password of a new user (USR-4 to USR-6, USR-16 to USR-23). */
public interface RecoveryService {

    /** How long a recovery link works (USR-19). */
    Duration LINK_LIFETIME = Duration.ofHours(1);

    /**
     * A visitor asks for a recovery email. The caller always shows the same message, whether or not
     * the address has an account (USR-18). No email is sent for an unknown address or for the
     * master account (USR-16), and at most three are sent per address per hour (USR-22). The time
     * this takes does not depend on whether an email was sent.
     *
     * @throws org.example.website.platform.api.TooManyRequests when this visitor asks too often
     */
    void request(String email, String clientAddress);

    /**
     * Sends the recovery email to a user, for a new user (USR-5) or when an admin sends it again
     * (USR-6). An earlier link of the user stops working.
     *
     * @throws org.example.website.platform.api.InvalidInput for the master account
     * @throws org.example.website.platform.api.ResourceNotFound for an unknown user
     * @throws org.example.website.platform.api.ServiceUnavailable when the email service failed
     */
    void sendTo(UUID userId);

    /**
     * True when the token is a valid recovery link. Wrong tokens count against the visitor.
     *
     * @throws org.example.website.platform.api.TooManyRequests after too many wrong tokens
     */
    boolean isValid(String token, String clientAddress);

    /**
     * Sets the new password, ends all sessions of the user and uses up the link (USR-19, USR-21).
     * The link is only used up when the password is accepted, so a typo does not waste it.
     *
     * @throws RecoveryLinkInvalid when the link is not valid
     * @throws org.example.website.platform.api.InvalidInput when the password breaks the rules
     * @throws org.example.website.platform.api.TooManyRequests after too many wrong tokens
     */
    void complete(String token, String newPassword, String clientAddress);
}
