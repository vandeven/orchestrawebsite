package org.example.website.platform.api;

/** Expected problem (ARC-8): HTTP 404. */
public class ResourceNotFound extends ApplicationError {

    public ResourceNotFound(String logMessage) {
        super(404, logMessage);
    }

    public ResourceNotFound() {
        this("The requested item does not exist.");
    }
}
