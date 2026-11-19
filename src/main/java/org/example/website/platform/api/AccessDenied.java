package org.example.website.platform.api;

/** Expected problem (ARC-8): HTTP 403. */
public class AccessDenied extends ApplicationError {

    private static final long serialVersionUID = 1L;

    public AccessDenied(String logMessage) {
        super(403, logMessage);
    }

    public AccessDenied() {
        this("The user is not allowed to do this.");
    }
}
