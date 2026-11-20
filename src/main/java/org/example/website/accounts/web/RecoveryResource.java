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
import org.example.website.accounts.api.PasswordPolicy;
import org.example.website.accounts.api.RecoveryLinkInvalid;
import org.example.website.accounts.api.RecoveryService;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.request.ClientAddress;
import org.jboss.resteasy.reactive.RestCookie;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;

/**
 * Password recovery: asking for an email, opening the link and choosing a password (USR-16 to
 * USR-23, HRD-1, HRD-2).
 *
 * <p>The link in the email carries the token. Opening it puts the token in a short-lived cookie and
 * redirects to a page without the token in the address, so it cannot stay in the history of the
 * browser or leak through the {@code Referer} header. These pages send {@code Referrer-Policy:
 * no-referrer} and are never cached.
 */
@Path("/")
@Access(AccessLevel.PUBLIC)
@Produces(MediaType.TEXT_HTML)
public class RecoveryResource {

    /**
     * Not a login cookie: it only holds the recovery link between the two steps, for 15 minutes.
     */
    static final String COOKIE = "__Host-recovery";

    static final int COOKIE_SECONDS = 15 * 60;

    @Inject
    @Location("accounts/forgot-password.html")
    Template forgotPage;

    @Inject
    @Location("accounts/set-password.html")
    Template setPage;

    @Inject
    @Location("accounts/recovery-invalid.html")
    Template invalidPage;

    @Inject RecoveryService recovery;
    @Inject PasswordPolicy policy;
    @Inject ClientAddress clientAddress;

    // Step 1: ask for an email (USR-17, USR-18)

    @GET
    @Path("forgot-password")
    public Response showRequest() {
        return forgot(200, "", null, false);
    }

    @POST
    @Path("forgot-password")
    public Response submitRequest(@RestForm String email) {
        String shown = email == null ? "" : email.strip();
        String normalized = AccountsRules.normalizeEmail(email);
        String error = null;
        if (normalized.isEmpty()) {
            error = "Enter your email address";
        } else if (!AccountsRules.validEmail(normalized)) {
            error = "Enter a valid email address";
        }
        if (error != null) {
            return forgot(400, shown, error, false);
        }
        // Too many requests give HTTP 429 through the error mapper. Everything else, with or
        // without an account, gives this one answer (USR-18).
        recovery.request(email, address());
        return forgot(200, "", null, true);
    }

    // Step 2: the link from the email (USR-19, HRD-2)

    @GET
    @Path("reset-password")
    public Response openLink(@RestQuery String token) {
        if (!recovery.isValid(token, address())) {
            return invalid();
        }
        return privatePage(Response.status(Response.Status.SEE_OTHER))
                .header("Location", "/set-password") // without the token in the address
                .cookie(cookie(token, COOKIE_SECONDS))
                .build();
    }

    // Step 3: choose a password (USR-20, USR-21)

    @GET
    @Path("set-password")
    public Response showSetPassword(@RestCookie(COOKIE) String token) {
        if (!recovery.isValid(token, address())) {
            return invalid();
        }
        return setPassword(200, null, null);
    }

    @POST
    @Path("set-password")
    public Response submitSetPassword(
            @RestCookie(COOKIE) String token,
            @RestForm String password,
            @RestForm String password2) {
        if (!recovery.isValid(token, address())) {
            return invalid();
        }
        // The rules are checked here for messages next to the fields, and again by the service
        Optional<String> problem = policy.problem(password);
        if (problem.isPresent()) {
            return setPassword(400, problem.get(), null);
        }
        if (!password.equals(password2)) {
            return setPassword(400, null, "The passwords are not the same");
        }
        try {
            recovery.complete(token, password, address());
        } catch (RecoveryLinkInvalid e) {
            return invalid();
        } catch (InvalidInput e) {
            return setPassword(400, "That password cannot be used, choose another one", null);
        }
        return privatePage(Response.status(Response.Status.SEE_OTHER))
                .header("Location", "/login?notice=password-set")
                .cookie(cookie("", 0))
                .build();
    }

    // Helpers

    private String address() {
        return clientAddress.current().orElse("unknown");
    }

    private Response forgot(int status, String email, String error, boolean sent) {
        String html =
                forgotPage
                        .data("currentPath", "/forgot-password")
                        .data("email", email)
                        .data("emailError", error)
                        .data("sent", sent)
                        .data("emailMax", AccountsRules.EMAIL_MAX)
                        .render();
        return privatePage(Response.status(status))
                .type(MediaType.TEXT_HTML_TYPE)
                .entity(html)
                .build();
    }

    private Response setPassword(int status, String passwordError, String confirmError) {
        String html =
                setPage.data("currentPath", "/set-password")
                        .data("passwordError", passwordError)
                        .data("confirmError", confirmError)
                        .data("min", AccountsRules.NEW_PASSWORD_MIN)
                        .data("max", AccountsRules.PASSWORD_MAX)
                        .render();
        return privatePage(Response.status(status))
                .type(MediaType.TEXT_HTML_TYPE)
                .entity(html)
                .build();
    }

    private Response invalid() {
        String html = invalidPage.data("currentPath", "/forgot-password").render();
        return privatePage(Response.status(400))
                .type(MediaType.TEXT_HTML_TYPE)
                .entity(html)
                .build();
    }

    /** Never cached, and the address of the page is not sent to other sites (HRD-2). */
    private static Response.ResponseBuilder privatePage(Response.ResponseBuilder builder) {
        return builder.header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer");
    }

    private static NewCookie cookie(String token, int seconds) {
        return new NewCookie.Builder(COOKIE)
                .value(token == null ? "" : token)
                .path("/")
                .maxAge(seconds)
                .httpOnly(true)
                .secure(true)
                // Lax, not Strict: the link in the email is a visit from another site, and the
                // cookie must come back on the redirect that follows
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }
}
