package org.example.website.accounts.api;

import java.util.Optional;

/** The user of the request that is being handled, from the session cookie. */
public interface CurrentUser {

    /** The logged-in user, or nothing for an anonymous visitor or an expired or ended session. */
    Optional<UserSummary> current();
}
