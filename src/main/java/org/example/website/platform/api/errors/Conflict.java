package org.example.website.platform.api.errors;

/** Expected problem (ARC-8): HTTP 409. */
public class Conflict extends ApplicationError {

    private static final long serialVersionUID = 1L;

    public Conflict(String logMessage) {
        super(409, logMessage);
    }

    public Conflict() {
        this("The request conflicts with the current state.");
    }
}
