package org.example.website.site.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.example.website.platform.api.LegalConfig;
import org.example.website.platform.api.PlatformConfig;
import org.example.website.platform.api.access.Access;
import org.example.website.platform.api.access.AccessLevel;

/**
 * What search engines and security researchers look for: {@code robots.txt}, {@code sitemap.xml}
 * and {@code /.well-known/security.txt} (plan: Search engines and sharing, HRD-11).
 */
@Path("/")
@Access(AccessLevel.PUBLIC)
public class SeoResource {

    /** The public pages worth indexing. {@code /about} is not here: it moves to {@code /}. */
    static final List<String> PUBLIC_PAGES =
            List.of("/", "/become-a-member", "/agenda", "/privacy", "/cookies", "/terms");

    /** What search engines must not visit: everything behind a login and the login pages. */
    static final List<String> BLOCKED =
            List.of(
                    "/members",
                    "/songs/",
                    "/users",
                    "/edit",
                    "/account",
                    "/login",
                    "/logout",
                    "/forgot-password",
                    "/reset-password");

    @Inject PlatformConfig config;
    @Inject LegalConfig legal;
    @Inject Clock clock;

    @GET
    @Path("robots.txt")
    @Produces("text/plain;charset=UTF-8")
    public Response robots() {
        StringBuilder text = new StringBuilder("User-agent: *\n");
        for (String path : BLOCKED) {
            text.append("Disallow: ").append(path).append('\n');
        }
        text.append("\nSitemap: ").append(base()).append("/sitemap.xml\n");
        return cached(text.toString());
    }

    @GET
    @Path("sitemap.xml")
    @Produces("application/xml;charset=UTF-8")
    public Response sitemap() {
        // A real XML writer, so every address is escaped whatever it contains
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");
            xml.writeStartElement("urlset");
            xml.writeDefaultNamespace("http://www.sitemaps.org/schemas/sitemap/0.9");
            for (String path : PUBLIC_PAGES) {
                xml.writeCharacters("\n  ");
                xml.writeStartElement("url");
                xml.writeStartElement("loc");
                xml.writeCharacters(base() + path);
                xml.writeEndElement();
                xml.writeEndElement();
            }
            xml.writeCharacters("\n");
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("The sitemap could not be written", e);
        }
        return cached(out.toString());
    }

    @GET
    @Path(".well-known/security.txt")
    @Produces("text/plain;charset=UTF-8")
    public Response securityTxt() {
        // The file must say when it expires (RFC 9116). It always says half a year ahead, so it
        // never runs out as long as the website is running.
        String expires =
                DateTimeFormatter.ISO_INSTANT.format(
                        clock.instant()
                                .plus(Duration.ofDays(180))
                                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        String text =
                "Contact: mailto:"
                        + legal.securityContact()
                        + "\n"
                        + "Expires: "
                        + expires
                        + "\n"
                        + "Policy: "
                        + legal.securityPolicyUrl()
                        + "\n"
                        + "Preferred-Languages: en\n"
                        + "Canonical: "
                        + base()
                        + "/.well-known/security.txt\n";
        return cached(text);
    }

    private String base() {
        String base = config.publicBaseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static Response cached(String body) {
        return Response.ok(body).header("Cache-Control", "public, max-age=3600").build();
    }
}
