package org.example.website.content.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.website.accounts.api.login.CurrentUser;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.content.api.texts.EditableText;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.access.Destructive;
import org.example.website.platform.api.errors.AccessDenied;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ResourceNotFound;
import org.example.website.platform.api.support.AmsterdamTime;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

/**
 * The edit pages for admins and the master (EDT-1 to EDT-8, EDT-18): the list of texts, the editor
 * with the HTML view, the preview, saving and clearing. The editor works without JavaScript as a
 * plain text area for HTML; with JavaScript it adds the visual editor. The server cleans every text
 * when it is saved, whatever the browser sent (HRD-5).
 */
@Path("/edit")
@Access(AccessLevel.ADMIN)
@Produces(MediaType.TEXT_HTML)
public class EditResource {

    @Inject
    @Location("content/edit-list.html")
    Template listPage;

    @Inject
    @Location("content/edit.html")
    Template editPage;

    @Inject
    @Location("content/edit-preview.html")
    Template previewPage;

    @Inject
    @Location("content/edit-clear.html")
    Template clearPage;

    @Inject EditableTexts texts;
    @Inject CurrentUser currentUser;

    /** The message after an action. Only a key from this list is accepted from the address. */
    private record Notice(String text, boolean error) {}

    private static final Map<String, Notice> NOTICES =
            Map.of(
                    "saved",
                            new Notice(
                                    "Saved. The page shows the new text. What you see below is exactly what is stored:"
                                            + " anything that is not allowed on the website has been removed.",
                                    false),
                    "cleared",
                            new Notice(
                                    "The text is cleared. The page shows its placeholder or hides the section.",
                                    false));

    /** One line of the list of texts. */
    public record Row(String key, String title, String shownOn, String changed) {}

    @GET
    public Response list(@RestQuery String notice) {
        List<Row> rows = new ArrayList<>();
        for (TextKey key : TextKey.values()) {
            EditableText text = texts.get(key);
            rows.add(
                    new Row(
                            key.key(),
                            key.title(),
                            key.shownOn(),
                            text.changedAt() == null
                                    ? "Never changed"
                                    : AmsterdamTime.dateTime(text.changedAt())));
        }
        return page(
                listPage.data("currentPath", "/edit")
                        .data("rows", rows)
                        .data("notice", notice(notice)),
                200);
    }

    @GET
    @Path("{key}")
    public Response edit(@RestPath String key, @RestQuery String notice) {
        TextKey textKey = key(key);
        return editor(textKey, texts.get(textKey).html(), notice(notice), 200);
    }

    /** Back from the preview to the editor with what was typed, so nothing is lost (EDT-7). */
    @POST
    @Path("{key}")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response resume(@RestPath String key, String body) {
        return editor(key(key), htmlOf(body), null, 200);
    }

    // The preview looks exactly like the public page and stores nothing (EDT-7)

    @POST
    @Path("{key}/preview")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response preview(@RestPath String key, String body) {
        TextKey textKey = key(key);
        String html = htmlOf(body);
        String clean = texts.clean(html);
        return page(
                previewPage
                        .data("currentPath", "/edit")
                        .data("key", textKey)
                        .data("title", previewTitle(textKey))
                        .data("html", clean)
                        .data("empty", clean.isBlank())
                        .data("submitted", html),
                200);
    }

    // Saving: always cleaned on the server; an empty text is a clear and asks first (EDT-18)

    @POST
    @Path("{key}/save")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response save(@RestPath String key, String body) {
        TextKey textKey = key(key);
        String html = htmlOf(body);
        String clean = texts.clean(html);
        if (clean.isBlank()) {
            return page(
                    clearPage
                            .data("currentPath", "/edit")
                            .data("key", textKey)
                            .data("submitted", html),
                    200);
        }
        try {
            texts.save(textKey, html, actor());
            return redirect("/edit/" + textKey.key(), "saved");
        } catch (InvalidInput e) {
            return editor(
                    textKey,
                    html,
                    new Notice("The text is too long. At most 200 KB of HTML is allowed.", true),
                    400);
        }
    }

    @POST
    @Path("{key}/clear")
    @Destructive
    public Response clear(@RestPath String key, @RestForm String confirm) {
        TextKey textKey = key(key);
        texts.clear(textKey, actor());
        return redirect("/edit", "cleared");
    }

    // Helpers

    private Response editor(TextKey key, String html, Notice notice, int status) {
        return page(
                editPage.data("currentPath", "/edit")
                        .data("key", key)
                        .data("html", html)
                        .data("notice", notice)
                        .data("imageBase", ""),
                status);
    }

    private Response page(io.quarkus.qute.TemplateInstance template, int status) {
        return Response.status(status)
                .entity(template.render())
                .type(MediaType.TEXT_HTML_TYPE)
                .header("Cache-Control", "no-store")
                .build();
    }

    private Response redirect(String path, String notice) {
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", path + "?notice=" + notice)
                .header("Cache-Control", "no-store")
                .build();
    }

    /**
     * The text arrives as a plain text body, {@code html=<the text>} (a form with {@code
     * enctype="text/plain"}), because a normal form field is limited to 2 KB for every form of the
     * website. The size of the body is limited by {@code quarkus.http.limits.max-body-size}.
     */
    static String htmlOf(String body) {
        if (body == null || !body.startsWith("html=")) {
            throw new InvalidInput("The text is missing");
        }
        String value = body.substring("html=".length());
        if (value.endsWith("\r\n")) {
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("\n")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static TextKey key(String key) {
        return TextKey.fromKey(key).orElseThrow(() -> new ResourceNotFound("No such text"));
    }

    private static Notice notice(String key) {
        return key == null ? null : NOTICES.get(key);
    }

    private static String previewTitle(TextKey key) {
        return switch (key) {
            case ABOUT -> "About";
            case BECOME_A_MEMBER -> "Become a member";
            case AUDITION -> "Audition";
            case AGENDA -> "Agenda";
        };
    }

    private UserSummary actor() {
        return currentUser.current().orElseThrow(() -> new AccessDenied("Not logged in"));
    }
}
