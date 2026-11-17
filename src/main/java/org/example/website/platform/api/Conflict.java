package org.example.website.platform.api;

/** Expected problem (ARC-8): HTTP 409. */
public class Conflict extends ApplicationError {

    public Conflict(String logMessage) {
        super(409, logMessage);
    }

    public Conflict() {
        this("The request conflicts with the current state.");
    }
}
