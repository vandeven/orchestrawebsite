package org.example.website.platform.api.mail;

/**
 * An email to send (design DES-11): always both a plain text and an HTML version. The address and
 * the subject and the optional reply address must not contain line breaks, so nobody can add
 * headers to the mail (HRD-7).
 */
public record Email(String to, String subject, String text, String html, String replyTo) {

    /** An email without a reply address of its own: replies go to the configured one, if any. */
    public Email(String to, String subject, String text, String html) {
        this(to, subject, text, html, null);
    }

    public Email {
        for (String value : new String[] {to, subject}) {
            if (value == null
                    || value.isBlank()
                    || value.indexOf('\r') >= 0
                    || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("The address and the subject must be one line");
            }
        }
        if (text == null || text.isBlank() || html == null || html.isBlank()) {
            throw new IllegalArgumentException("An email has a plain text and an HTML version");
        }
        if (replyTo != null
                && (replyTo.isBlank()
                        || replyTo.indexOf('\r') >= 0
                        || replyTo.indexOf('\n') >= 0)) {
            throw new IllegalArgumentException("The reply address must be one line");
        }
    }
}
