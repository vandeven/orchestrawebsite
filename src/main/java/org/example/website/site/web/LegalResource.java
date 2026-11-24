package org.example.website.site.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.example.website.accounts.api.login.LoginService;
import org.example.website.accounts.api.recovery.RecoveryService;
import org.example.website.platform.api.LegalConfig;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;

/**
 * The privacy, cookies and terms of use pages (LEG-1 to LEG-10). The texts are in the templates, in
 * the source code, and change through a reviewed change. The retention periods come from the same
 * values the application uses.
 */
@Path("/")
@Access(AccessLevel.PUBLIC)
@Produces(MediaType.TEXT_HTML)
public class LegalResource {

    /** The date the texts were last changed. Change it with the text (LEG-2). */
    static final LocalDate UPDATED = LocalDate.of(2026, 11, 23);

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    /** The cookies the website sets (LEG-6). A test checks that no other cookie is ever set. */
    public record CookieInfo(String name, String purpose, String lifetime) {}

    /** One line of the retention table (LEG-4). */
    public record Retention(String data, String kept) {}

    @Inject
    @Location("site/privacy.html")
    Template privacy;

    @Inject
    @Location("site/cookies.html")
    Template cookies;

    @Inject
    @Location("site/terms.html")
    Template terms;

    @Inject LegalConfig legal;

    @GET
    @Path("privacy")
    public Response privacy() {
        return page(
                privacy.data("currentPath", "/privacy")
                        .data("updated", UPDATED.format(DATE))
                        .data("privacyEmail", legal.privacyEmail())
                        .data("retention", retention())
                        .render());
    }

    @GET
    @Path("cookies")
    public Response cookies() {
        return page(
                cookies.data("currentPath", "/cookies")
                        .data("updated", UPDATED.format(DATE))
                        .data("cookies", cookieList())
                        .render());
    }

    @GET
    @Path("terms")
    public Response terms() {
        return page(
                terms.data("currentPath", "/terms").data("updated", UPDATED.format(DATE)).render());
    }

    /** The cookies, with lifetimes from the values the application really uses. */
    List<CookieInfo> cookieList() {
        return List.of(
                new CookieInfo(
                        "__Host-session",
                        "Keeps you logged in after you log in. Strictly necessary.",
                        "At most " + words(LoginService.SESSION_LIFETIME)),
                new CookieInfo(
                        "__Host-recovery",
                        "Remembers that you opened your password link while you choose a new"
                                + " password. Strictly necessary.",
                        "15 minutes"));
    }

    /** The retention table of the privacy page (LEG-4). */
    List<Retention> retention() {
        return List.of(
                new Retention(
                        "Account (email address, role, password hash, dates)",
                        "Until the person is removed"),
                new Retention("Login session", "At most " + words(LoginService.SESSION_LIFETIME)),
                new Retention("Password recovery link", words(RecoveryService.LINK_LIFETIME)),
                new Retention(
                        "Application logs and the admin audit trail",
                        legal.logRetentionDays() + " days"),
                new Retention("Database backups", legal.backupRetentionDays() + " days"),
                new Retention("Deleted member files and songs", legal.deletedFilesDays() + " days"),
                new Retention("Contact form messages", "Not stored on the website"));
    }

    /** {@code 24 hours} is written as "1 day", {@code 1 hour} as "1 hour". */
    static String words(Duration duration) {
        long hours = duration.toHours();
        if (hours >= 24 && hours % 24 == 0) {
            long days = hours / 24;
            return days + (days == 1 ? " day" : " days");
        }
        return hours + (hours == 1 ? " hour" : " hours");
    }

    private Response page(String html) {
        return Response.ok(html, MediaType.TEXT_HTML_TYPE)
                .header("Cache-Control", AboutResource.PUBLIC_CACHE)
                .header("Vary", "Cookie")
                .build();
    }
}
