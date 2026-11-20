package org.example.website.platform.api.mail;

import java.util.List;

/**
 * The one simple template of all emails (DES-11): a plain header with the name of the orchestra,
 * the text, an optional link and a footer. The HTML version uses system fonts and inline styles, no
 * scripts, no tracking pixels and no remote images, so it reads the same with images switched off.
 * The colors are written out because email programs do not know CSS variables; they match the
 * placeholder tokens in {@code tokens.css} and are replaced together with them.
 */
public final class EmailLayout {

    private static final String FONT = "system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif";

    private EmailLayout() {}

    /** The plain text version. */
    public static String text(
            String siteName,
            String heading,
            List<String> paragraphs,
            String linkLabel,
            String linkUrl,
            String footer) {
        StringBuilder text = new StringBuilder();
        text.append(siteName).append("\n\n").append(heading).append("\n\n");
        for (String paragraph : paragraphs) {
            text.append(paragraph).append("\n\n");
        }
        if (linkUrl != null) {
            text.append(linkLabel).append(":\n").append(linkUrl).append("\n\n");
        }
        text.append("--\n").append(footer).append('\n');
        return text.toString();
    }

    /** The HTML version. Everything that comes from outside is escaped. */
    public static String html(
            String siteName,
            String heading,
            List<String> paragraphs,
            String linkLabel,
            String linkUrl,
            String footer) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>")
                .append(escape(heading))
                .append("</title></head>")
                .append(
                        "<body style=\"margin:0;padding:0;background:#faf8f5;color:#1f1a1c;font-family:")
                .append(FONT)
                .append(";font-size:16px;line-height:1.5\">")
                .append("<div style=\"max-width:36rem;margin:0 auto;padding:16px\">")
                .append(
                        "<p style=\"margin:0 0 16px;font-size:20px;font-weight:bold;color:#7a1f3d\">")
                .append(escape(siteName))
                .append("</p>")
                .append("<div style=\"background:#ffffff;border:1px solid #857a7e;padding:16px\">")
                .append("<h1 style=\"margin:0 0 16px;font-size:24px;line-height:1.2\">")
                .append(escape(heading))
                .append("</h1>");
        for (String paragraph : paragraphs) {
            html.append("<p style=\"margin:0 0 16px\">").append(escape(paragraph)).append("</p>");
        }
        if (linkUrl != null) {
            html.append("<p style=\"margin:0 0 16px\"><a href=\"")
                    .append(escape(linkUrl))
                    .append("\" style=\"display:inline-block;padding:12px 24px;background:#7a1f3d;")
                    .append("color:#ffffff;text-decoration:none;font-weight:bold\">")
                    .append(escape(linkLabel))
                    .append("</a></p>")
                    // The address is also written out, for programs that do not show buttons
                    .append(
                            "<p style=\"margin:0 0 16px;font-size:14px\">Or copy this address into your browser:<br>")
                    .append("<span style=\"word-break:break-all\">")
                    .append(escape(linkUrl))
                    .append("</span></p>");
        }
        html.append("</div><p style=\"margin:16px 0 0;font-size:14px;color:#5c5256\">")
                .append(escape(footer))
                .append("</p></div></body></html>");
        return html.toString();
    }

    /** Escapes text for HTML, also inside attributes. */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
