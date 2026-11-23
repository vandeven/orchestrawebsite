package org.example.website.contact.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.website.accounts.api.rules.AccountsRules;
import org.example.website.accounts.api.users.AdminContacts;
import org.example.website.contact.api.ContactMessages;
import org.example.website.platform.api.PlatformConfig;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.platform.api.limits.RateLimiter;
import org.example.website.platform.api.mail.Email;
import org.example.website.platform.api.mail.EmailLayout;
import org.example.website.platform.api.mail.Mailer;
import org.jboss.logging.Logger;

/**
 * The contact form (BAM-12 to BAM-17). The message is checked, counted against the limits and
 * emailed to every admin and the master, one email each, from the website's own address with the
 * sender as the reply address. Nothing about the message is stored or logged.
 */
@ApplicationScoped
public class ContactMessagesImpl implements ContactMessages {

    private static final Logger LOG = Logger.getLogger(ContactMessagesImpl.class);

    @Inject AdminContacts admins;
    @Inject Mailer mailer;
    @Inject RateLimiter limiter;
    @Inject PlatformConfig platform;
    @Inject ContactConfig config;

    @Override
    public Map<String, String> problems(String senderEmail, String subject, String message) {
        Map<String, String> problems = new LinkedHashMap<>();
        String address = AccountsRules.normalizeEmail(senderEmail);
        if (address.isEmpty()) {
            problems.put("email", "Enter your email address");
        } else if (!AccountsRules.validEmail(address)) {
            problems.put("email", "Enter a valid email address");
        }
        if (subject == null || subject.strip().isEmpty()) {
            problems.put("subject", "Enter a subject");
        } else if (subject.strip().length() > SUBJECT_MAX) {
            problems.put("subject", "The subject can have at most " + SUBJECT_MAX + " characters");
        } else if (hasControlCharacter(subject.strip(), false)) {
            problems.put("subject", "The subject must be one line without special characters");
        }
        if (message == null || message.strip().isEmpty()) {
            problems.put("message", "Enter a message");
        } else if (message.strip().length() > MESSAGE_MAX) {
            problems.put("message", "The message can have at most " + MESSAGE_MAX + " characters");
        }
        return problems;
    }

    @Override
    public void send(String senderEmail, String subject, String message, String clientAddress) {
        Map<String, String> problems = problems(senderEmail, subject, message);
        if (!problems.isEmpty()) {
            throw new InvalidInput("The message does not follow the rules");
        }
        // The limits count every valid message, also the ones that fail to send afterwards
        if (clientAddress != null) {
            var visitor =
                    limiter.hit("contact-visitor:" + hash(clientAddress), Duration.ofHours(1));
            if (visitor.count() > config.perVisitorPerHour()) {
                LOG.warn("Contact message refused: one visitor sent too many");
                throw new TooManyRequests(
                        "Too many messages", Math.max(1, visitor.untilReset().toSeconds()));
            }
        }
        var site = limiter.hit("contact-day", Duration.ofDays(1));
        if (site.count() > config.dailyLimit()) {
            LOG.error("ALERT: the daily limit of contact messages was reached");
            throw new TooManyRequests(
                    "Too many messages today", Math.max(1, site.untilReset().toSeconds()));
        }

        String from = AccountsRules.normalizeEmail(senderEmail);
        String cleanSubject = subject.strip();
        String body = cleanMessage(message);
        List<String> recipients = admins.emailAddresses();
        if (recipients.isEmpty()) {
            LOG.error(
                    "ALERT: a contact message could not be sent: there is no admin to receive it");
            throw new ServiceUnavailable("Nobody to send the message to");
        }
        int delivered = 0;
        for (String recipient : recipients) {
            try {
                mailer.send(email(recipient, from, cleanSubject, body));
                delivered++;
            } catch (ServiceUnavailable e) {
                // The mailer has logged why. The others still get the message.
            }
        }
        if (delivered == 0) {
            LOG.error("ALERT: a contact message could not be sent to any admin");
            throw new ServiceUnavailable("The message could not be sent");
        }
        if (delivered < recipients.size()) {
            LOG.errorf(
                    "ALERT: a contact message reached %d of %d admins",
                    delivered, recipients.size());
        } else {
            LOG.infof("Contact message sent to %d admins", delivered);
        }
    }

    /** Text only: the sender's words are escaped by the layout and never become markup. */
    private Email email(String to, String from, String subject, String message) {
        String name = platform.name();
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(
                "A message from the contact form of the website. Reply to this email to answer it.");
        paragraphs.add("From: " + from);
        paragraphs.add("Subject: " + subject);
        paragraphs.add(message);
        String footer = "You receive this because you are an admin of the website of " + name + ".";
        return new Email(
                to,
                "Contact form: " + subject,
                EmailLayout.text(
                        name, "Message from the contact form", paragraphs, null, null, footer),
                EmailLayout.html(
                        name, "Message from the contact form", paragraphs, null, null, footer),
                from);
    }

    /** Line ends become \n and other control characters are dropped. */
    static String cleanMessage(String message) {
        String text = message.strip().replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean hasControlCharacter(String value, boolean allowLineBreaks) {
        for (char c : value.toCharArray()) {
            if (Character.isISOControl(c) && !(allowLineBreaks && (c == '\n' || c == '\r'))) {
                return true;
            }
        }
        return false;
    }

    /** A hash, so no network address is ever stored (LGG-5). */
    private static String hash(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
