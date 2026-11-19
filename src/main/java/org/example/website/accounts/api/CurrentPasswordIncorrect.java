package org.example.website.accounts.api;

import org.example.website.platform.api.ApplicationError;

/**
 * Expected problem (ARC-8): the current password typed when changing a password is not correct
 * (ACC-4).
 */
public class CurrentPasswordIncorrect extends ApplicationError {

    private static final long serialVersionUID = 1L;

    public CurrentPasswordIncorrect() {
        super(400, "The current password is not correct");
    }
}
