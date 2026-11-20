package org.example.website.platform.web;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;
import org.example.website.platform.api.mail.Outbox;

/**
 * Development only: shows the email the application "sent" to the outbox, so password recovery can
 * be tried by hand without an email service. It is not part of the production build or of the
 * tests.
 */
@Path("/dev/outbox")
@Access(AccessLevel.PUBLIC)
@IfBuildProfile("dev")
public class DevOutboxResource {

    @Inject Outbox outbox;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String show() {
        StringBuilder text = new StringBuilder("Development outbox (nothing is really sent)\n\n");
        outbox.sent()
                .forEach(email ->
                        text.append("To: ")
                                .append(email.to())
                                .append("\nSubject: ")
                                .append(email.subject())
                                .append("\n\n")
                                .append(email.text())
                                .append("\n=====\n\n"));
        return text.toString();
    }
}
