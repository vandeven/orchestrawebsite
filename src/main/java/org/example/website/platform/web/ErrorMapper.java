package org.example.website.platform.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.example.website.platform.api.LegalConfig;
import org.example.website.platform.api.PlatformConfig;
import org.example.website.platform.api.errors.ApplicationError;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.platform.api.request.RequestIds;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * The one place that turns errors into responses (ARC-8, HOS-32): a friendly English page for
 * browsers and an RFC 9457 problem document for JSON clients. Responses never contain technical
 * details; they show the request ID so a problem can be reported (LGG-3).
 */
public class ErrorMapper {

    private static final Logger LOG = Logger.getLogger(ErrorMapper.class);
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    /** Title and message per status. Everything not listed uses the generic text of its class. */
    private static final Map<Integer, String[]> TEXTS =
            Map.of(
                    400,
                            new String[] {
                                "Check your input",
                                "We could not process this request. Check what you entered and try again."
                            },
                    403, new String[] {"Access denied", "You do not have access to this page."},
                    404,
                            new String[] {
                                "Page not found",
                                "We could not find this page. It may have moved or no longer exists."
                            },
                    429,
                            new String[] {
                                "Too many attempts", "Too many attempts, try again in a moment."
                            },
                    409,
                            new String[] {
                                "Not possible right now",
                                "This could not be done because something changed in the meantime. Reload the page and try again."
                            },
                    500,
                            new String[] {
                                "Something went wrong",
                                "Something went wrong on our side. Try again later. If it keeps happening, tell an admin and mention the code below."
                            },
                    503,
                            new String[] {
                                "Temporarily unavailable",
                                "This part of the website is not available right now. Please try again in a few minutes."
                            });

    @Inject
    @Location("platform/error.html")
    Template errorPage;

    @Inject CurrentVertxRequest currentRequest;

    @Inject PlatformConfig config;

    @Inject LegalConfig legal;

    @ServerExceptionMapper
    public Response expected(ApplicationError error) {
        if (error.status() >= 500) {
            LOG.warnf("Expected problem (%d): %s", error.status(), error.getMessage());
        } else {
            LOG.debugf("Expected problem (%d): %s", error.status(), error.getMessage());
        }
        Response response = respond(error.status());
        if (error instanceof TooManyRequests tooMany) {
            return Response.fromResponse(response)
                    .header("Retry-After", tooMany.retryAfterSeconds())
                    .build();
        }
        return response;
    }

    @ServerExceptionMapper
    public Response web(WebApplicationException error) {
        int status = error.getResponse().getStatus();
        if (status < 400) {
            return error.getResponse(); // redirects and the like are not errors
        }
        if (status >= 500) {
            LOG.error("Error while handling a request", error);
        }
        return respond(status);
    }

    @ServerExceptionMapper
    public Response unexpected(Throwable error) {
        LOG.error("Unexpected error while handling a request", error);
        return respond(500);
    }

    private Response respond(int status) {
        RoutingContext context = currentRequest.getCurrent();
        String requestId = context == null ? null : context.get(RequestIds.KEY);
        String[] text = textFor(status);
        String accept = context == null ? null : context.request().getHeader("Accept");

        Response.ResponseBuilder response =
                Response.status(status).header("Cache-Control", "no-store");
        if (wantsJson(accept)) {
            String body =
                    "{\"type\":\"about:blank\",\"title\":%s,\"status\":%d,\"requestId\":%s}"
                            .formatted(json(text[0]), status, json(requestId));
            return response.type(PROBLEM_JSON).entity(body).build();
        }
        String page =
                errorPage
                        .data("name", config.name())
                        .data(
                                "assetVersion",
                                java.net.URLEncoder.encode(
                                        config.version(), java.nio.charset.StandardCharsets.UTF_8))
                        .data("foundationName", legal.foundationName())
                        .data("foundationAddress", legal.address())
                        .data("foundationRegistration", legal.registrationNumber())
                        .data("foundationEmail", legal.contactEmail())
                        .data("status", status)
                        .data("title", text[0])
                        .data("message", text[1])
                        .data("requestId", requestId)
                        .data("version", status == 500 ? config.version() : null)
                        .render();
        return response.type(MediaType.TEXT_HTML_TYPE).entity(page).build();
    }

    private static String[] textFor(int status) {
        String[] known = TEXTS.get(status);
        if (known != null) {
            return known;
        }
        if (status >= 500) {
            return TEXTS.get(500);
        }
        return new String[] {
            "Request not possible", "This request is not possible. Go back and try something else."
        };
    }

    /** JSON only when the client asks for it and does not prefer a page. */
    static boolean wantsJson(String accept) {
        return accept != null
                && (accept.contains("json") || accept.contains("problem"))
                && !accept.contains("text/html");
    }

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> out.append(c < 0x20 ? "" : String.valueOf(c));
            }
        }
        return out.append('"').toString();
    }
}
