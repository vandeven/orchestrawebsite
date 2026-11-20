package org.example.website.accounts.api.recovery;

import org.example.website.platform.api.errors.ApplicationError;

/**
 * Expected problem (ARC-8): the recovery link is unknown, used, expired or was replaced (USR-19).
 */
public class RecoveryLinkInvalid extends ApplicationError {

    private static final long serialVersionUID = 1L;

    public RecoveryLinkInvalid() {
        super(400, "The recovery link is not valid");
    }
}
