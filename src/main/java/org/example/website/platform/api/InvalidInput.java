package org.example.website.platform.api;

/** Expected problem (ARC-8): HTTP 400. */
public class InvalidInput extends ApplicationError {

    public InvalidInput(String logMessage) {
        super(400, logMessage);
    }

    public InvalidInput() {
        this("The input is not valid.");
    }
}
