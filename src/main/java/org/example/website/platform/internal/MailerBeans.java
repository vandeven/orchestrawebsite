package org.example.website.platform.internal;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.List;
import org.example.website.platform.api.Email;
import org.example.website.platform.api.Mailer;
import org.example.website.platform.api.ServiceUnavailable;
import org.jboss.logging.Logger;

/**
 * The email service of the application (ARC-6, ARC-11). The configuration is checked when the
 * application starts and the application refuses to start when it is invalid.
 */
@ApplicationScoped
public class MailerBeans {

    private static final Logger LOG = Logger.getLogger(MailerBeans.class);

    @Inject MailConfig config;

    @Inject OutboxMailer outbox;

    private volatile BrevoClient brevo;

    void onStart(@Observes StartupEvent event) {
        List<String> problems =
                MailConfigRules.problems(
                        config.provider(),
                        config.allowOutbox(),
                        LaunchMode.current() == LaunchMode.NORMAL,
                        config.senderAddress(),
                        config.senderName(),
                        config.replyTo(),
                        config.brevoApiKey(),
                        config.brevoUrl());
        if (!problems.isEmpty()) {
            LOG.errorf("Email configuration check failed: %s", String.join("; ", problems));
            throw new IllegalStateException(
                    "Invalid configuration, the application does not start: "
                            + String.join("; ", problems));
        }
        LOG.infof("Email configuration check passed (provider %s)", config.provider());
    }

    @Produces
    @ApplicationScoped
    public Mailer mailer() {
        return email -> deliver(email);
    }

    private void deliver(Email email) {
        if ("outbox".equals(config.provider())) {
            if (outbox.failing()) {
                throw new ServiceUnavailable("The outbox is set to fail");
            }
            outbox.add(email);
            return;
        }
        BrevoClient client = brevo;
        if (client == null) {
            client =
                    new BrevoClient(
                            config.brevoUrl(),
                            config.brevoApiKey().orElseThrow(),
                            config.senderAddress(),
                            config.senderName(),
                            config.replyTo().orElse(null));
            brevo = client;
        }
        client.send(email);
    }
}
