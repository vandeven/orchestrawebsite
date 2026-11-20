package org.example.website.accounts.api.login;

import java.time.Duration;
import org.example.website.accounts.api.users.UserSummary;

/**
 * The result of a login attempt. A failure carries no reason on purpose: a wrong password, an
 * unknown address and an account without a password must look the same (LOG-5).
 */
public sealed interface LoginResult {

    /** The login worked. The token goes into the session cookie and is never stored or logged. */
    record LoggedIn(String sessionToken, UserSummary user) implements LoginResult {}

    /**
     * Too many failed logins: the attempt was not checked at all. Try again after the wait
     * (LOG-13).
     */
    record Throttled(Duration retryAfter) implements LoginResult {}

    /** The login did not work. */
    record Failed() implements LoginResult {}
}
