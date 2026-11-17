package org.example.website.platform.api;

/** Expected problem (ARC-8): HTTP 503. */
public class ServiceUnavailable extends ApplicationError {

    private static final long serialVersionUID = 1L;

    public ServiceUnavailable(String logMessage) {
        super(503, logMessage);
    }

    public ServiceUnavailable() {
        this("A service the website depends on is not available.");
    }
}
