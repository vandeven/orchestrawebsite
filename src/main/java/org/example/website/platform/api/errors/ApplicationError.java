package org.example.website.platform.api.errors;

/**
 * Base class of the expected problems that components report (ARC-8). The message is written for
 * the log, never shown to the visitor.
 */
public abstract class ApplicationError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    protected ApplicationError(int status, String logMessage) {
        super(logMessage);
        this.status = status;
    }

    /** The HTTP status code that belongs to this problem. */
    public int status() {
        return status;
    }
}
