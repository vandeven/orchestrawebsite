package org.example.website.platform.internal;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.example.website.platform.api.Email;
import org.example.website.platform.api.ServiceUnavailable;
import org.jboss.logging.Logger;

/**
 * Sends mail through Brevo's transactional email API (HOS-21). Only the status of an answer is
 * logged: the answer can contain the address of the person.
 */
class BrevoClient {

    private static final Logger LOG = Logger.getLogger(BrevoClient.class);

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String url;
    private final String apiKey;
    private final String senderAddress;
    private final String senderName;
    private final String replyTo;

    BrevoClient(
            String url, String apiKey, String senderAddress, String senderName, String replyTo) {
        this.url = url;
        this.apiKey = apiKey;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
        this.replyTo = replyTo;
    }

    void send(Email email) {
        JsonObject body =
                new JsonObject()
                        .put(
                                "sender",
                                new JsonObject()
                                        .put("name", senderName)
                                        .put("email", senderAddress))
                        .put("to", new JsonArray().add(new JsonObject().put("email", email.to())))
                        .put("subject", email.subject())
                        .put("textContent", email.text())
                        .put("htmlContent", email.html());
        if (replyTo != null) {
            body.put("replyTo", new JsonObject().put("email", replyTo));
        }
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .header("api-key", apiKey)
                        .header("content-type", "application/json")
                        .header("accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.encode()))
                        .build();
        try {
            HttpResponse<Void> response =
                    http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                LOG.errorf(
                        "The email service refused a message (status %d)", response.statusCode());
                throw new ServiceUnavailable("Email service answered " + response.statusCode());
            }
        } catch (IOException e) {
            LOG.errorf("The email service could not be reached (%s)", e.getClass().getSimpleName());
            throw new ServiceUnavailable("Email service not reachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailable("Interrupted while sending email");
        }
    }
}
