package org.example.website.platform.internal;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/** Configuration of the email service (ARC-11, HOS-21). Checked when the application starts. */
@ConfigMapping(prefix = "mail")
interface MailConfig {

    /**
     * {@code brevo} sends real email; {@code outbox} only keeps it in memory (tests and local
     * work).
     */
    String provider();

    /** Allow the outbox in production. Only the container smoke test switches this on. */
    @WithDefault("false")
    boolean allowOutbox();

    /** The address mail is sent from: on the orchestra's own domain (SPF, DKIM, DMARC). */
    String senderAddress();

    String senderName();

    Optional<String> replyTo();

    /** The Brevo API key. Never logged. Required when the provider is {@code brevo}. */
    Optional<String> brevoApiKey();

    @WithDefault("https://api.brevo.com/v3/smtp/email")
    String brevoUrl();
}
