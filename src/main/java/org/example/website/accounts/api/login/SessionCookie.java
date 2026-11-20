package org.example.website.accounts.api.login;

/** The one cookie of the website (LOG-11, HRD-8). */
public final class SessionCookie {

    /** The {@code __Host-} prefix makes browsers insist on Secure, Path=/ and no Domain. */
    public static final String NAME = "__Host-session";

    private SessionCookie() {}
}
