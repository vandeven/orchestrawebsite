package org.example.website.platform.internal.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.LogRecord;
import org.example.website.TestLogs;
import org.example.website.platform.api.errors.ServiceUnavailable;
import org.example.website.platform.api.mail.Email;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HOS-21: the adapter for Brevo, tested against a small local server that plays the email service.
 */
class BrevoClientTest {

    private static final String KEY = "FAKE-NOT-A-SECRET-brevo-key-0123456789";

    private HttpServer server;
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private final List<String> apiKeys = new CopyOnWriteArrayList<>();
    private final List<String> methods = new CopyOnWriteArrayList<>();
    private volatile int status = 201;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/v3/smtp/email",
                exchange -> {
                    requestBodies.add(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    apiKeys.add(String.valueOf(exchange.getRequestHeaders().getFirst("api-key")));
                    methods.add(exchange.getRequestMethod());
                    byte[] answer =
                            "{\"message\":\"someone@example.test is blocked\"}"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, answer.length);
                    exchange.getResponseBody().write(answer);
                    exchange.close();
                });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
    }

    private static Email email() {
        return new Email(
                "member@example.test",
                "Choose your password",
                "plain text body",
                "<p>html body</p>");
    }

    @Test
    void sendsTheMailThroughTheApiWithTheKeyAndBothVersions() {
        new BrevoClient(url(), KEY, "no-reply@example.org", "The Orchestra", null).send(email());

        assertEquals(List.of("POST"), methods);
        assertEquals(List.of(KEY), apiKeys);
        JsonObject body = new JsonObject(requestBodies.get(0));
        assertEquals("no-reply@example.org", body.getJsonObject("sender").getString("email"));
        assertEquals("The Orchestra", body.getJsonObject("sender").getString("name"));
        assertEquals(
                "member@example.test", body.getJsonArray("to").getJsonObject(0).getString("email"));
        assertEquals("Choose your password", body.getString("subject"));
        assertEquals("plain text body", body.getString("textContent"));
        assertEquals("<p>html body</p>", body.getString("htmlContent"));
        assertFalse(body.containsKey("replyTo"));
    }

    @Test
    void addsTheReplyToAddressWhenOneIsConfigured() {
        new BrevoClient(url(), KEY, "no-reply@example.org", "The Orchestra", "board@example.org")
                .send(email());
        assertEquals(
                "board@example.org",
                new JsonObject(requestBodies.get(0)).getJsonObject("replyTo").getString("email"));
    }

    @Test
    void aRefusalIsAServiceErrorAndTheAnswerIsNeverLogged() {
        status = 400;
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        assertThrows(
                                ServiceUnavailable.class,
                                () ->
                                        new BrevoClient(url(), KEY, "a@example.org", "N", null)
                                                .send(email())));
        assertTrue(logs.stream().anyMatch(r -> TestLogs.everything(r).contains("status 400")));
        for (LogRecord record : logs) {
            String text = TestLogs.everything(record);
            assertFalse(text.contains("someone@example.test"), "the answer can hold an address");
            assertFalse(text.contains("member@example.test"));
            assertFalse(text.contains(KEY));
        }
    }

    @Test
    void everyServerErrorIsAServiceError() {
        for (int code : new int[] {401, 429, 500, 503}) {
            status = code;
            assertThrows(
                    ServiceUnavailable.class,
                    () -> new BrevoClient(url(), KEY, "a@example.org", "N", null).send(email()),
                    "status " + code);
        }
    }

    @Test
    void anUnreachableServiceIsAServiceErrorToo() {
        server.stop(0);
        List<LogRecord> logs =
                TestLogs.capture(() ->
                        assertThrows(
                                ServiceUnavailable.class,
                                () ->
                                        new BrevoClient(url(), KEY, "a@example.org", "N", null)
                                                .send(email())));
        for (LogRecord record : logs) {
            assertFalse(TestLogs.everything(record).contains(KEY));
        }
    }
}
