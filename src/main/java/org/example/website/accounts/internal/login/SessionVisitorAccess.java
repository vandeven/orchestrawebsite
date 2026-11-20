package org.example.website.accounts.internal.login;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.example.website.accounts.api.login.CurrentUser;
import org.example.website.accounts.api.login.LoginService;
import org.example.website.accounts.api.login.SessionCookie;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.access.VisitorAccess;

/**
 * Tells the platform what the visitor of the current request may open, from the session cookie
 * (ARC-7). It replaces the default that treats everybody as anonymous. The lookup is done once per
 * request.
 */
@ApplicationScoped
class SessionVisitorAccess implements VisitorAccess, CurrentUser {

    private static final String REQUEST_KEY = "accounts.currentUser";

    @Inject CurrentVertxRequest currentRequest;
    @Inject LoginService loginService;

    @Override
    public AccessLevel currentLevel() {
        return current().map(user -> user.role().level()).orElse(AccessLevel.PUBLIC);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<UserSummary> current() {
        RoutingContext context = currentRequest.getCurrent();
        if (context == null) {
            return Optional.empty();
        }
        Object cached = context.get(REQUEST_KEY);
        if (cached != null) {
            return (Optional<UserSummary>) cached;
        }
        Cookie cookie = context.request().getCookie(SessionCookie.NAME);
        Optional<UserSummary> user =
                cookie == null ? Optional.empty() : loginService.userForSession(cookie.getValue());
        context.put(REQUEST_KEY, user);
        return user;
    }
}
