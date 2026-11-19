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
import java.util.Locale;
import java.util.Optional;
import org.example.website.accounts.api.AccountService;
import org.example.website.accounts.api.AccountsRules;
import org.example.website.accounts.api.CurrentPasswordIncorrect;
import org.example.website.accounts.api.CurrentUser;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.PasswordPolicy;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.SessionCookie;
import org.example.website.accounts.api.UserSummary;
import org.example.website.platform.api.Access;
import org.example.website.platform.api.AccessDenied;
import org.example.website.platform.api.AccessLevel;
import org.example.website.platform.api.ClientAddress;
import org.example.website.platform.api.InvalidInput;
import org.jboss.resteasy.reactive.RestCookie;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;

/**
 * The My account page: the user's email address and role, and changing their own password (ACC-1 to
 * ACC-9).
 */
@Path("/account")
@Access(AccessLevel.MEMBER)
@Produces(MediaType.TEXT_HTML)
public class AccountResource {

    @Inject
    @Location("accounts/account.html")
    Template accountPage;

    @Inject AccountService account;
    @Inject CurrentUser currentUser;
    @Inject PasswordPolicy policy;
    @Inject ClientAddress clientAddress;

    @GET
    public Response show(@RestQuery String notice) {
        return page(200, "password-changed".equals(notice), null, null, null);
    }

    @POST
    public Response changePassword(
            @RestCookie(SessionCookie.NAME) String sessionToken,
            @RestForm String current,
            @RestForm String password,
            @RestForm String password2) {
        UserSummary user = user();
        if (user.role() == Role.MASTER) {
            throw new AccessDenied("The master password is not changed on the website"); // ACC-8
        }
        // Messages next to the fields (ACC-5). The service checks every rule again.
        String currentError =
                current == null || current.isEmpty() ? "Enter your current password" : null;
        Optional<String> rule = policy.problem(password);
        String newError = rule.orElse(null);
        String confirmError = null;
        if (newError == null && currentError == null && password.equals(current)) {
            newError = "The new password must be different from the current one";
        }
        if (newError == null && !password.equals(password2)) {
            confirmError = "The passwords are not the same";
        }
        if (currentError != null || newError != null || confirmError != null) {
            return page(400, false, currentError, newError, confirmError);
        }
        try {
            String newToken =
                    account.changePassword(
                            sessionToken,
                            current,
                            password,
                            clientAddress.current().orElse("unknown"));
            // Too many attempts give HTTP 429 through the error mapper
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location", "/account?notice=password-changed")
                    .header("Cache-Control", "no-store")
                    .cookie(sessionCookie(newToken))
                    .build();
        } catch (CurrentPasswordIncorrect e) {
            // One general message (ACC-4); the passwords are never shown again
            return page(400, false, "The current password is not correct", null, null);
        } catch (InvalidInput e) {
            return page(400, false, null, "That password cannot be used, choose another one", null);
        }
    }

    private UserSummary user() {
        return currentUser.current().orElseThrow(() -> new AccessDenied("Not logged in"));
    }

    private Response page(
            int status,
            boolean changed,
            String currentError,
            String newError,
            String confirmError) {
        UserSummary user = user();
        String html =
                accountPage
                        .data("currentPath", "/account")
                        .data("email", user.email())
                        .data(
                                "role",
                                user.role().name().charAt(0)
                                        + user.role().name().substring(1).toLowerCase(Locale.ROOT))
                        .data("isMaster", user.role() == Role.MASTER)
                        .data("changed", changed)
                        .data("currentError", currentError)
                        .data("newError", newError)
                        .data("confirmError", confirmError)
                        .data("min", AccountsRules.NEW_PASSWORD_MIN)
                        .data("max", AccountsRules.PASSWORD_MAX)
                        .render();
        return Response.status(status)
                .type(MediaType.TEXT_HTML_TYPE)
                .entity(html)
                .header("Cache-Control", "no-store")
                .build();
    }

    /** The new session of this device: the same kind of cookie as at login (LOG-11). */
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
}
