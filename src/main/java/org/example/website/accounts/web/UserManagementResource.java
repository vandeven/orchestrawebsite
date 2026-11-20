package org.example.website.accounts.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.example.website.accounts.api.login.CurrentUser;
import org.example.website.accounts.api.rules.AccountsRules;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserManagement;
import org.example.website.accounts.api.users.UserManagement.Actions;
import org.example.website.accounts.api.users.UserManagement.Query;
import org.example.website.accounts.api.users.UserManagement.Sort;
import org.example.website.accounts.api.users.UserManagement.UserRow;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.access.Destructive;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.Conflict;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.example.website.platform.api.support.AmsterdamTime;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

/**
 * The user management page for admins and the master (USR-1 to USR-13, USR-22 to USR-24). The page
 * only shows what the acting user may do; the service checks every request again. Removing a user
 * and every rights change ask for confirmation: a confirmation page that works without JavaScript,
 * and the shared dialog when JavaScript is available (plan: Confirming destructive actions).
 */
@Path("/users")
@Access(AccessLevel.ADMIN)
@Produces(MediaType.TEXT_HTML)
public class UserManagementResource {

    @Inject
    @Location("accounts/users.html")
    Template overviewPage;

    @Inject
    @Location("accounts/user-confirm.html")
    Template confirmPage;

    @Inject UserManagement management;
    @Inject CurrentUser currentUser;

    /**
     * The message after an action. Only a key from this list is accepted; no text from the address
     * is shown.
     */
    private record Notice(String text, boolean error) {}

    private static final Map<String, Notice> NOTICES =
            Map.of(
                    "added",
                            new Notice(
                                    "User added. A recovery email has been sent, so they can choose a password.",
                                    false),
                    "added-no-email",
                            new Notice(
                                    "User added, but the recovery email could not be sent. Use \"Send email again\" later.",
                                    true),
                    "resent", new Notice("The recovery email has been sent.", false),
                    "resend-failed",
                            new Notice(
                                    "The recovery email could not be sent. Try again later.", true),
                    "removed", new Notice("User removed. They have lost access.", false),
                    "made-admin", new Notice("The user is now an admin.", false),
                    "made-member",
                            new Notice(
                                    "Admin rights taken away. The user is now a member.", false));

    // The overview (USR-7, USR-8) and adding users (USR-2 to USR-5)

    @GET
    public Response overview(
            @RestQuery String sort,
            @RestQuery String dir,
            @RestQuery String role,
            @RestQuery String notice) {
        return overviewPage(200, parse(sort, dir, role), notice, "", null);
    }

    @POST
    public Response add(@RestForm String email) {
        String shown = email == null ? "" : email.strip();
        String normalized = AccountsRules.normalizeEmail(email);
        String error = null;
        if (normalized.isEmpty()) {
            error = "Enter the email address of the new user";
        } else if (!AccountsRules.validEmail(normalized)) {
            error = "Enter a valid email address";
        }
        if (error != null) {
            return overviewPage(400, parse(null, null, null), null, shown, error);
        }
        try {
            var result = management.add(actor(), email);
            return redirect(result.recoveryEmailSent() ? "added" : "added-no-email");
        } catch (Conflict e) {
            // Admins may be told that an address has an account (USR-2, USR-18)
            return overviewPage(
                    409,
                    parse(null, null, null),
                    null,
                    shown,
                    "This email address already has an account");
        } catch (InvalidInput e) {
            return overviewPage(
                    400, parse(null, null, null), null, shown, "Enter a valid email address");
        }
    }

    // Sending the email again (USR-6): reversible, so no confirmation

    @POST
    @Path("{id}/resend")
    public Response resend(@RestPath String id) {
        try {
            management.resendRecovery(actor(), userId(id));
            return redirect("resent");
        } catch (ServiceUnavailable e) {
            return redirect("resend-failed");
        }
    }

    // Removing a user (USR-9, USR-12)

    @GET
    @Path("{id}/remove")
    public Response confirmRemove(@RestPath String id) {
        UserRow row = management.find(actor(), userId(id));
        if (!row.actions().remove()) {
            throw new AccessDenied("Not allowed to remove this user");
        }
        return confirm(
                row,
                "remove",
                "Remove user",
                "You are about to remove "
                        + row.user().email()
                        + ". Their account and their sessions are deleted at once and they lose access. This cannot be undone.");
    }

    @POST
    @Path("{id}/remove")
    @Destructive
    public Response remove(@RestPath String id, @RestForm String confirm) {
        management.remove(actor(), userId(id));
        return redirect("removed");
    }

    // Rights changes (USR-10, USR-12): always with a confirmation

    @GET
    @Path("{id}/make-admin")
    public Response confirmMakeAdmin(@RestPath String id) {
        UserRow row = management.find(actor(), userId(id));
        if (!row.actions().makeAdmin()) {
            throw new AccessDenied("Not allowed to make this user an admin");
        }
        return confirm(
                row,
                "make-admin",
                "Make admin",
                row.user().email()
                        + " will get all admin rights at once: they can add and remove members and make members admins. Only the master can take admin rights away again.");
    }

    @POST
    @Path("{id}/make-admin")
    @Destructive
    public Response makeAdmin(@RestPath String id, @RestForm String confirm) {
        management.makeAdmin(actor(), userId(id));
        return redirect("made-admin");
    }

    @GET
    @Path("{id}/make-member")
    public Response confirmMakeMember(@RestPath String id) {
        UserRow row = management.find(actor(), userId(id));
        if (!row.actions().makeMember()) {
            throw new AccessDenied("Not allowed to take away admin rights");
        }
        return confirm(
                row,
                "make-member",
                "Take away admin rights",
                row.user().email()
                        + " becomes a member and loses access to the admin pages at once. You can make them an admin again later.");
    }

    @POST
    @Path("{id}/make-member")
    @Destructive
    public Response makeMember(@RestPath String id, @RestForm String confirm) {
        management.makeMember(actor(), userId(id));
        return redirect("made-member");
    }

    // Helpers

    private UUID actor() {
        return currentUser.current().orElseThrow(() -> new AccessDenied("Not logged in")).id();
    }

    /** An ID that is not a UUID is a 404 before anything is looked up (HRD-3). */
    private static UUID userId(String id) {
        try {
            if (id == null || id.length() != 36) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFound("Not a user ID");
        }
    }

    /** Sorting and filtering values must come from the allowed lists (USR-8). */
    private static Query parse(String sort, String dir, String role) {
        Sort column =
                switch (sort == null || sort.isEmpty() ? "email" : sort) {
                    case "email" -> Sort.EMAIL;
                    case "role" -> Sort.ROLE;
                    case "added" -> Sort.ADDED;
                    case "last-login" -> Sort.LAST_LOGIN;
                    case "recovery-email" -> Sort.RECOVERY_EMAIL;
                    default -> throw new InvalidInput("Unknown sort column");
                };
        boolean ascending =
                switch (dir == null || dir.isEmpty() ? "asc" : dir) {
                    case "asc" -> true;
                    case "desc" -> false;
                    default -> throw new InvalidInput("Unknown sort direction");
                };
        Role filter =
                switch (role == null || role.isEmpty() ? "all" : role) {
                    case "all" -> null;
                    case "member" -> Role.MEMBER;
                    case "admin" -> Role.ADMIN;
                    case "master" -> Role.MASTER;
                    default -> throw new InvalidInput("Unknown role filter");
                };
        return new Query(column, ascending, filter);
    }

    private Response redirect(String noticeKey) {
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/users?notice=" + noticeKey)
                .header("Cache-Control", "no-store")
                .build();
    }

    /** One row of the table, with the dates in Amsterdam time. */
    public record Row(
            String id,
            String email,
            String role,
            String added,
            String lastLogin,
            String recoveryEmail,
            Actions actions) {}

    /** A column heading that sorts the table when it is followed. */
    public record Heading(String label, String href, String ariaSort) {}

    private Response overviewPage(
            int status, Query query, String noticeKey, String email, String emailError) {
        List<Row> rows = new ArrayList<>();
        for (UserRow row : management.list(actor(), query)) {
            var user = row.user();
            rows.add(
                    new Row(
                            user.id().toString(),
                            user.email(),
                            label(user.role()),
                            AmsterdamTime.date(user.createdAt()),
                            user.lastLoginAt() == null
                                    ? "Never logged in"
                                    : AmsterdamTime.dateTime(user.lastLoginAt()),
                            user.recoveryEmailSentAt() == null
                                    ? "Never sent"
                                    : AmsterdamTime.dateTime(user.recoveryEmailSentAt()),
                            row.actions()));
        }
        Notice notice = noticeKey == null ? null : NOTICES.get(noticeKey);
        String html =
                overviewPage
                        .data("currentPath", "/users")
                        .data("rows", rows)
                        .data("headings", headings(query))
                        .data(
                                "roleFilter",
                                query.role() == null
                                        ? "all"
                                        : query.role().name().toLowerCase(Locale.ROOT))
                        .data("sortKey", sortKey(query.sort()))
                        .data("dirKey", query.ascending() ? "asc" : "desc")
                        .data("notice", notice)
                        .data("email", email)
                        .data("emailError", emailError)
                        .data("emailMax", AccountsRules.EMAIL_MAX)
                        .data(
                                "hasActions",
                                rows.stream()
                                        .anyMatch(r ->
                                                r.actions().remove()
                                                        || r.actions().makeAdmin()
                                                        || r.actions().makeMember()))
                        .render();
        return Response.status(status)
                .type(MediaType.TEXT_HTML_TYPE)
                .entity(html)
                .header("Cache-Control", "no-store")
                .build();
    }

    private static List<Heading> headings(Query query) {
        List<Heading> headings = new ArrayList<>();
        String role = query.role() == null ? "all" : query.role().name().toLowerCase(Locale.ROOT);
        for (Sort sort : Sort.values()) {
            boolean current = sort == query.sort();
            String direction =
                    current && query.ascending()
                            ? "desc"
                            : "asc"; // following the link flips the direction
            headings.add(
                    new Heading(
                            switch (sort) {
                                case EMAIL -> "Email address";
                                case ROLE -> "Role";
                                case ADDED -> "Added";
                                case LAST_LOGIN -> "Last login";
                                case RECOVERY_EMAIL -> "Last recovery email";
                            },
                            "/users?sort=" + sortKey(sort) + "&dir=" + direction + "&role=" + role,
                            current ? (query.ascending() ? "ascending" : "descending") : "none"));
        }
        return headings;
    }

    private static String sortKey(Sort sort) {
        return switch (sort) {
            case EMAIL -> "email";
            case ROLE -> "role";
            case ADDED -> "added";
            case LAST_LOGIN -> "last-login";
            case RECOVERY_EMAIL -> "recovery-email";
        };
    }

    private static String label(Role role) {
        return role.name().charAt(0) + role.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private Response confirm(UserRow row, String action, String title, String text) {
        String html =
                confirmPage
                        .data("currentPath", "/users")
                        .data("title", title)
                        .data("text", text)
                        .data("email", row.user().email())
                        .data("postPath", "/users/" + row.user().id() + "/" + action)
                        .data("button", title)
                        .render();
        return Response.ok(html, MediaType.TEXT_HTML_TYPE)
                .header("Cache-Control", "no-store")
                .build();
    }
}
