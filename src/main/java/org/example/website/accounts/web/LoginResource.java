package org.example.website.accounts.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.example.website.accounts.api.AccountsRules;
import org.example.website.accounts.api.CurrentUser;
import org.example.website.accounts.api.LoginResult;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.SessionCookie;
import org.example.website.platform.api.Access;
import org.example.website.platform.api.AccessLevel;
import org.example.website.platform.api.ClientAddress;
import org.example.website.platform.api.ReturnPath;
import org.jboss.resteasy.reactive.RestCookie;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;

/** The login page and logging out (LOG-1 to LOG-12). */
@Path("/")
@Access(AccessLevel.PUBLIC)
@Produces(MediaType.TEXT_HTML)
public class LoginResource {

    /** Where a user lands after logging in when no page was asked for (LOG-4). */
    static final String DEFAULT_PAGE = "/members";

    static final String GENERAL_ERROR = "Email address or password is incorrect";
    static final String TOO_MANY_ATTEMPTS = "Too many attempts, try again in a moment";

    @Inject
    @Location("accounts/login.html")
    Template loginPage;

    @Inject LoginService loginService;
    @Inject CurrentUser currentUser;
    @Inject ClientAddress clientAddress;

    @GET
    @Path("login")
    public Response show(@RestQuery("return") String returnTo) {
        if (currentUser.current().isPresent()) {
            return redirect(DEFAULT_PAGE); // LOG-7
        }
        return page(200, "", null, null, null, returnTo);
    }

    @POST
    @Path("login")
    public Response submit(
            @RestForm String email,
            @RestForm String password,
            @RestForm("return") String returnTo,
            @RestCookie(SessionCookie.NAME) String oldSessionToken) {
        String shownEmail = email == null ? "" : email.strip();
        String normalized = AccountsRules.normalizeEmail(email);

        // Format errors are shown next to the field. They do not depend on any account (LOG-5).
        String emailError = null;
        if (normalized.isEmpty()) {
            emailError = "Enter your email address";
        } else if (!AccountsRules.validEmail(normalized)) {
            emailError = "Enter a valid email address";
        }
        String passwordError =
                password == null || password.isEmpty() ? "Enter your password" : null;
        if (emailError != null || passwordError != null) {
            return page(400, shownEmail, emailError, passwordError, null, returnTo);
        }

        LoginResult result =
                loginService.login(email, password, clientAddress.current().orElse("unknown"));
        if (result instanceof LoginResult.Throttled throttled) {
            // HTTP 429 with the wait (LOG-13). The address stays; the password is not shown again.
            long seconds = Math.max(1, (throttled.retryAfter().toMillis() + 999) / 1000);
            return Response.fromResponse(
                            page(429, shownEmail, null, null, TOO_MANY_ATTEMPTS, returnTo))
                    .header("Retry-After", seconds)
                    .build();
        }
        if (result instanceof LoginResult.LoggedIn loggedIn) {
            // A session from before the login is ended, so it cannot be used afterwards
            if (oldSessionToken != null) {
                loginService.logout(oldSessionToken);
            }
            Optional<String> target = ReturnPath.sanitize(returnTo);
            return redirectBuilder(target.orElse(DEFAULT_PAGE))
                    .cookie(sessionCookie(loggedIn.sessionToken()))
                    .build();
        }
        // One message for every kind of failure (LOG-5): the address stays, the password is emptied
        return page(200, shownEmail, null, null, GENERAL_ERROR, returnTo);
    }

    /**
     * Logging out is allowed for everyone, so a second click or an expired session does no harm.
     */
    @POST
    @Path("logout")
    public Response logout(@RestCookie(SessionCookie.NAME) String sessionToken) {
        if (sessionToken != null) {
            loginService.logout(sessionToken);
        }
        return redirectBuilder("/")
                .cookie(expiredSessionCookie())
                .header("Clear-Site-Data", "\"cookies\"") // HRD-8
                .build();
    }

    private Response page(
            int status,
            String email,
            String emailError,
            String passwordError,
            String generalError,
            String returnTo) {
        String html =
                loginPage
                        .data("currentPath", "/login")
                        .data("email", email)
                        .data("emailError", emailError)
                        .data("passwordError", passwordError)
                        .data("generalError", generalError)
                        .data("returnTo", ReturnPath.sanitize(returnTo).orElse(null))
                        .data("emailMax", AccountsRules.EMAIL_MAX)
                        .data("passwordMax", AccountsRules.PASSWORD_MAX)
                        .render();
        return Response.status(status)
                .type(MediaType.TEXT_HTML_TYPE)
                .entity(html)
                .header("Cache-Control", "no-store") // LOG-12
                .build();
    }

    private static Response redirect(String path) {
        return redirectBuilder(path).build();
    }

    /**
     * A 303 to a path on this website, written as a relative address (never built from the Host
     * header).
     */
    private static Response.ResponseBuilder redirectBuilder(String path) {
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", path)
                .header("Cache-Control", "no-store");
    }

    private static NewCookie sessionCookie(String token) {
        return new NewCookie.Builder(SessionCookie.NAME)
                .value(token)
                .path("/")
                .maxAge((int) LoginService.SESSION_LIFETIME.toSeconds())
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    private static NewCookie expiredSessionCookie() {
        return new NewCookie.Builder(SessionCookie.NAME)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }
}
