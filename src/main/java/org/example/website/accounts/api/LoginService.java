package org.example.website.accounts.api;

import java.time.Duration;
import java.util.Optional;

/** Logging in and out, and finding out who a session belongs to (LOG-3 to LOG-11). */
public interface LoginService {

    /** How long a session lasts after logging in, whatever the activity (LOG-10). */
    Duration SESSION_LIFETIME = Duration.ofHours(24);

    /**
     * Checks the credentials and starts a session. The same work is done whether or not the address
     * has an account, so the response time does not reveal it (LOG-13). Too many failures give
     * {@link LoginResult.Throttled} before anything is checked.
     *
     * @param clientAddress the visitor's address (from the platform), only used to build hashed
     *     counters; null when it is not known
     */
    LoginResult login(String email, String password, String clientAddress);

    /**
     * A login without a known visitor address, for callers that are not a web request. Only the
     * limit for the account in total applies.
     */
    default LoginResult login(String email, String password) {
        return login(email, password, null);
    }

    /**
     * The user a session token belongs to, read fresh on every call so role changes count at once.
     */
    Optional<UserSummary> userForSession(String sessionToken);

    /** Ends the session on the server. Unknown tokens are ignored. */
    void logout(String sessionToken);
}
