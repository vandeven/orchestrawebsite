package org.example.website.platform.internal.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The validation rules for the email configuration. Messages never contain the values. */
final class MailConfigRules {

    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s\\p{Cntrl}]+@[^@\\s\\p{Cntrl}]+\\.[^@\\s\\p{Cntrl}]+$");

    private MailConfigRules() {}

    static List<String> problems(
            String provider,
            boolean allowOutbox,
            boolean production,
            String senderAddress,
            String senderName,
            Optional<String> replyTo,
            Optional<String> brevoApiKey,
            String brevoUrl) {
        List<String> problems = new ArrayList<>();
        boolean brevo = "brevo".equals(provider);
        boolean outbox = "outbox".equals(provider);
        if (!brevo && !outbox) {
            problems.add("MAIL_PROVIDER must be brevo or outbox");
        }
        if (outbox && production && !allowOutbox) {
            problems.add("MAIL_PROVIDER=outbox sends nothing and is not allowed in production");
        }
        if (brevo && brevoApiKey.filter(key -> key.length() >= 20).isEmpty()) {
            problems.add("BREVO_API_KEY is required for MAIL_PROVIDER=brevo");
        }
        if (brevo && (brevoUrl == null || !brevoUrl.startsWith("https://")) && production) {
            problems.add("mail.brevo-url must be an https:// address");
        }
        if (senderAddress == null
                || !EMAIL.matcher(senderAddress).matches()
                || senderAddress.length() > 254) {
            problems.add("MAIL_SENDER_ADDRESS must be a valid email address");
        }
        if (senderName == null
                || senderName.isBlank()
                || senderName.length() > 100
                || senderName.chars().anyMatch(Character::isISOControl)) {
            problems.add("MAIL_SENDER_NAME must be 1 to 100 characters on one line");
        }
        if (replyTo.isPresent()
                && (!EMAIL.matcher(replyTo.get()).matches() || replyTo.get().length() > 254)) {
            problems.add("MAIL_REPLY_TO must be a valid email address");
        }
        return problems;
    }
}
