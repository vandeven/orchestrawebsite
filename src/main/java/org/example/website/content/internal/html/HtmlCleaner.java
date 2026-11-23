package org.example.website.content.internal.html;

import java.util.regex.Pattern;
import org.owasp.html.ElementPolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Cleans the HTML of the editable texts with an allowlist (EDT, HRD-5). Only what the editor can
 * make is kept (EDT-4). Everything else is removed: scripts, styles, event handlers, iframes,
 * forms, unsafe links, and images from anywhere but the website's own image storage (EDT-14).
 * Admins cannot choose fonts or colors: the look comes from the website's own classes.
 */
public final class HtmlCleaner {

    private static final Pattern CLASSES =
            Pattern.compile(
                    "(align-(left|center|right)|img-(small|medium|full))"
                            + "( (align-(left|center|right)|img-(small|medium|full)))*");

    private final PolicyFactory policy;

    /**
     * {@code imageBaseUrl} is the address images must start with, for example {@code https://...}.
     */
    public HtmlCleaner(String imageBaseUrl) {
        String base = imageBaseUrl == null ? "" : imageBaseUrl;
        // Links to other websites open in a new tab, without handing over the opener (EDT-4)
        ElementPolicy links =
                (element, attributes) -> {
                    for (int i = 0; i + 1 < attributes.size(); i += 2) {
                        if (attributes.get(i).equalsIgnoreCase("href")
                                && attributes.get(i + 1).regionMatches(true, 0, "http", 0, 4)) {
                            attributes.add("target");
                            attributes.add("_blank");
                            break;
                        }
                    }
                    return element;
                };
        this.policy =
                new HtmlPolicyBuilder()
                        .allowElements(
                                "h2",
                                "h3",
                                "h4",
                                "p",
                                "br",
                                "strong",
                                "b",
                                "em",
                                "i",
                                "u",
                                "s",
                                "strike",
                                "ul",
                                "ol",
                                "li",
                                "blockquote",
                                "hr",
                                "table",
                                "thead",
                                "tbody",
                                "tr",
                                "th",
                                "td",
                                "img")
                        .allowElements(links, "a")
                        .allowUrlProtocols("http", "https", "mailto")
                        .allowAttributes("href")
                        .onElements("a")
                        .allowAttributes("target")
                        .matching(Pattern.compile("_blank"))
                        .onElements("a")
                        .requireRelsOnLinks("noopener", "noreferrer")
                        .allowAttributes("class")
                        .matching(CLASSES)
                        .onElements("p", "h2", "h3", "h4", "blockquote", "img", "table", "td", "th")
                        .allowAttributes("src")
                        .matching((elementName, attributeName, value) ->
                                !base.isEmpty() && value.startsWith(base) ? value : null)
                        .onElements("img")
                        .allowAttributes("alt")
                        .matching(Pattern.compile(".{0,250}", Pattern.DOTALL))
                        .onElements("img")
                        .allowAttributes("width", "height")
                        .matching(Pattern.compile("[0-9]{1,5}"))
                        .onElements("img")
                        .allowAttributes("colspan", "rowspan")
                        .matching(Pattern.compile("[1-9][0-9]?"))
                        .onElements("td", "th")
                        .allowAttributes("scope")
                        .matching(Pattern.compile("col|row"))
                        .onElements("th")
                        .toFactory();
    }

    /** The cleaned HTML. Never null. */
    public String clean(String html) {
        return html == null ? "" : policy.sanitize(html);
    }
}
