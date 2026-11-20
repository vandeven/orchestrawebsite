package org.example.website.platform.api.errors;

/** Expected problem (ARC-8): HTTP 400. */
public class InvalidInput extends ApplicationError {

    private static final long serialVersionUID = 1L;

    public InvalidInput(String logMessage) {
        super(400, logMessage);
    }

    public InvalidInput() {
        this("The input is not valid.");
    }
}
