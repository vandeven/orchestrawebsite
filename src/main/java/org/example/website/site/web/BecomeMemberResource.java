package org.example.website.site.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.example.website.contact.api.ContactMessages;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.platform.api.request.ClientAddress;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;

/**
 * The Become a member page (BAM-1 to BAM-17): the page text, the audition section when there is
 * audition text, and the contact form. The form sends its data to this address; the page itself is
 * the same for everyone and is cached for a short time for anonymous visitors.
 */
@Path("/become-a-member")
@Access(AccessLevel.PUBLIC)
@Produces(MediaType.TEXT_HTML)
public class BecomeMemberResource {

    @Inject
    @Location("site/become-a-member.html")
    Template page;

    @Inject EditableTexts texts;
    @Inject ContactMessages contact;
    @Inject ClientAddress clientAddress;

    @GET
    public Response show(@RestQuery String notice) {
        boolean sent = "sent".equals(notice);
        return render(200, "", "", "", Map.of(), null, sent, true);
    }

    @POST
    public Response send(
            @RestForm String email,
            @RestForm String subject,
            @RestForm String message,
            @RestForm String website) {
        String shownEmail = email == null ? "" : email.strip();
        String shownSubject = subject == null ? "" : subject;
        String shownMessage = message == null ? "" : message;
        // The hidden field is for programs only: when it is filled in, the message is dropped and
        // the sender sees the normal confirmation (BAM-16)
        if (website != null && !website.isBlank()) {
            return sentRedirect();
        }
        Map<String, String> problems = contact.problems(email, subject, message);
        if (!problems.isEmpty()) {
            return render(
                    400, shownEmail, shownSubject, shownMessage, problems, null, false, false);
        }
        try {
            contact.send(email, subject, message, clientAddress.current().orElse(null));
            return sentRedirect();
        } catch (TooManyRequests e) {
            return render(
                    429,
                    shownEmail,
                    shownSubject,
                    shownMessage,
                    Map.of(),
                    "You have sent too many messages. Please try again later.",
                    false,
                    false);
        } catch (ServiceUnavailable e) {
            return render(
                    503,
                    shownEmail,
                    shownSubject,
                    shownMessage,
                    Map.of(),
                    "Your message could not be sent. Please try again later, or use the email address above.",
                    false,
                    false);
        } catch (InvalidInput e) {
            return render(
                    400,
                    shownEmail,
                    shownSubject,
                    shownMessage,
                    Map.of(),
                    "Check your message and try again.",
                    false,
                    false);
        }
    }

    private Response sentRedirect() {
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/become-a-member?notice=sent#contact")
                .header("Cache-Control", "no-store")
                .build();
    }

    private Response render(
            int status,
            String email,
            String subject,
            String message,
            Map<String, String> problems,
            String generalError,
            boolean sent,
            boolean cacheable) {
        var text = texts.get(TextKey.BECOME_A_MEMBER);
        var audition = texts.get(TextKey.AUDITION);
        Response.ResponseBuilder builder =
                Response.status(status)
                        .entity(
                                page.data("currentPath", "/become-a-member")
                                        .data("text", text)
                                        .data("audition", audition)
                                        .data("email", email)
                                        .data("subject", subject)
                                        .data("message", message)
                                        .data("hasProblems", !problems.isEmpty())
                                        .data("emailError", problems.get("email"))
                                        .data("subjectError", problems.get("subject"))
                                        .data("messageError", problems.get("message"))
                                        .data("generalError", generalError)
                                        .data("sent", sent)
                                        .data("subjectMax", ContactMessages.SUBJECT_MAX)
                                        .data("messageMax", ContactMessages.MESSAGE_MAX)
                                        .render())
                        .type(MediaType.TEXT_HTML_TYPE);
        if (cacheable) {
            builder.header("Cache-Control", AboutResource.PUBLIC_CACHE).header("Vary", "Cookie");
        } else {
            builder.header("Cache-Control", "no-store");
        }
        return builder.build();
    }
}
