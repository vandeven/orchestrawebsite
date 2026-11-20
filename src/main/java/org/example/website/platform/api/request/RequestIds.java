package org.example.website.platform.api.request;

/** Names for the request ID (LGG-3), shared by the platform and the components that log. */
public final class RequestIds {

    /** The response header that carries the request ID. */
    public static final String HEADER = "X-Request-ID";

    /** The key of the request ID in the logging context and in the request data. */
    public static final String KEY = "requestId";

    private RequestIds() {}
}
