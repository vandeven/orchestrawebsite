package org.example.website.contact;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.users.AdminContacts;
import org.example.website.accounts.api.users.Role;
import org.example.website.contact.api.ContactMessages;
import org.example.website.platform.api.errors.InvalidInput;
import org.example.website.platform.api.errors.TooManyRequests;
import org.example.website.platform.api.mail.Email;
import org.example.website.platform.api.mail.Outbox;
import org.example.website.platform.internal.limits.InMemoryRateLimiter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The contact form of the Become a member page (BAM-12 to BAM-17). */
@QuarkusTest
class ContactFormTest {

    @Inject ContactMessages contact;
    @Inject AdminContacts admins;
    @Inject Outbox outbox;
    @Inject TestAccounts accounts;
    @Inject InMemoryRateLimiter limits;

    @BeforeEach
    void start() {
        limits.clear();
        outbox.clear();
        accounts.newUser(Role.ADMIN);
    }

    @AfterEach
    void stop() {
        outbox.failSends(false);
        outbox.clear();
        limits.clear();
    }

    private static String address() {
        return TestAccounts.randomAddress();
    }

    private RequestSpecification post() {
        return given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Origin", TestAccounts.ORIGIN)
                .header("X-Forwarded-For", address());
    }

    private Response submit(String email, String subject, String message) {
        return post().formParam("email", email)
                .formParam("subject", subject)
                .formParam("message", message)
                .post("/become-a-member");
    }

    // BAM-14: who gets it and what it looks like

    @Test
    void aMessageIsEmailedToEveryAdminAndTheMasterWithTheSenderAsReplyAddress() {
        Set<String> recipients = Set.copyOf(admins.emailAddresses());
        assertTrue(recipients.contains(TestAccounts.MASTER_EMAIL), "the master receives it too");
        assertTrue(recipients.size() >= 2);

        contact.send("Anna@Example.org ", "Join the choir", "Hello,\nI can sing.", "10.1.1.1");

        List<Email> sent = outbox.sent();
        assertEquals(recipients, sent.stream().map(Email::to).collect(Collectors.toSet()));
        assertEquals(recipients.size(), sent.size(), "one email each");
        for (Email email : sent) {
            assertEquals("anna@example.org", email.replyTo());
            assertEquals("Contact form: Join the choir", email.subject());
            assertTrue(email.text().contains("I can sing."));
            assertTrue(email.text().contains("anna@example.org"));
        }
    }

    @Test
    void textWithHtmlOrLinksArrivesAsPlainTextAndNeverBecomesMarkup() {
        contact.send(
                "a@example.org",
                "<b>Hi</b>",
                "<script>alert(1)</script> <a href=\"https://evil.example\">click</a>",
                "10.1.1.2");
        Email email = outbox.sent().get(0);
        assertFalse(email.html().contains("<script>"));
        assertFalse(email.html().contains("<a href=\"https://evil.example\""));
        assertTrue(email.html().contains("&lt;script&gt;"));
        assertTrue(email.text().contains("<script>alert(1)</script>"));
    }

    @Test
    void controlCharactersAreRemovedFromTheMessageAndALineBreakInTheSubjectIsRefused() {
        contact.send("a@example.org", "Subject", "one\r\ntwo\u0000three\u0007", "10.1.1.3");
        assertTrue(outbox.sent().get(0).text().contains("one\ntwothree"));
        for (String subject : new String[] {"a\nBcc: x@evil.example", "a\rb", "a\u0000b"}) {
            assertThrows(
                    InvalidInput.class,
                    () -> contact.send("a@example.org", subject, "m", "10.1.1.4"),
                    subject);
        }
        for (String sender :
                new String[] {"a@example.org\nBcc: x@evil.example", "a b@example.org"}) {
            assertThrows(
                    InvalidInput.class, () -> contact.send(sender, "s", "m", "10.1.1.5"), sender);
        }
        assertEquals(admins.emailAddresses().size(), outbox.sent().size(), "only the first one");
    }

    // The rules, with their boundaries

    @Test
    void theLengthsHaveExactBoundaries() {
        assertTrue(contact.problems("a@example.org", "s".repeat(200), "m".repeat(5000)).isEmpty());
        assertTrue(contact.problems("a@example.org", "s".repeat(201), "m").containsKey("subject"));
        assertTrue(contact.problems("a@example.org", "s", "m".repeat(5001)).containsKey("message"));
        assertTrue(
                contact.problems("a@example.org", "s", "m").isEmpty(), "one character is enough");
        String longEmail = "a".repeat(250) + "@b.co";
        assertTrue(contact.problems(longEmail, "s", "m").containsKey("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "no-at-sign", "a@b", "@example.org", "a@@example.org"})
    void aWrongSenderAddressIsRefused(String email) {
        assertTrue(contact.problems(email, "s", "m").containsKey("email"), email);
    }

    @Test
    void everyFieldIsRequired() {
        var problems = contact.problems(null, " ", "");
        assertEquals(Set.of("email", "subject", "message"), problems.keySet());
    }

    // BAM-16: limits

    @Test
    void oneVisitorMayOnlySendAFewMessagesAnHour() {
        for (int i = 0; i < 5; i++) {
            contact.send("a@example.org", "s", "m", "10.2.2.2");
        }
        assertThrows(
                TooManyRequests.class, () -> contact.send("a@example.org", "s", "m", "10.2.2.2"));
        // Another visitor is not affected
        contact.send("a@example.org", "s", "m", "10.2.2.3");
    }

    @Test
    void theWholeWebsiteAcceptsAtMostTheDailyLimit() {
        for (int i = 0; i < 50; i++) {
            contact.send("a@example.org", "s", "m", "10.3." + (i / 200) + "." + (i % 200));
        }
        assertThrows(
                TooManyRequests.class, () -> contact.send("a@example.org", "s", "m", "10.9.9.9"));
    }

    @Test
    void invalidMessagesDoNotCountAgainstTheLimits() {
        for (int i = 0; i < 20; i++) {
            assertThrows(InvalidInput.class, () -> contact.send("wrong", "s", "m", "10.4.4.4"));
        }
        contact.send("a@example.org", "s", "m", "10.4.4.4");
    }

    // The page

    @Test
    void aValidFormShowsTheConfirmationAndSendsTheEmails() {
        Response response = submit("anna@example.org", "Hi", "Can I join?");
        assertEquals(303, response.statusCode());
        assertTrue(response.header("Location").contains("/become-a-member?notice=sent"));
        assertEquals(admins.emailAddresses().size(), outbox.sent().size());
        Document page = Jsoup.parse(given().get("/become-a-member?notice=sent").asString());
        assertTrue(page.select(".alert-success").text().contains("Your message has been sent"));
    }

    @Test
    void aFilledInHoneypotSendsNothingAndLooksLikeSuccess() {
        Response response =
                post().formParam("email", "bot@example.org")
                        .formParam("subject", "Buy now")
                        .formParam("message", "spam")
                        .formParam("website", "https://spam.example")
                        .post("/become-a-member");
        assertEquals(303, response.statusCode());
        assertTrue(outbox.sent().isEmpty());
    }

    @Test
    void mistakesShowNextToTheFieldsAndKeepTheInput() {
        Response response = submit("not-an-address", "", "My message \"quoted\" <b>x</b>");
        assertEquals(400, response.statusCode());
        Document page = Jsoup.parse(response.asString());
        assertEquals("true", page.selectFirst("#contact-email").attr("aria-invalid"));
        assertTrue(page.selectFirst("#contact-email-error").text().contains("valid email"));
        assertTrue(page.selectFirst("#contact-subject-error").text().contains("subject"));
        assertTrue(page.select("#contact-message-error").isEmpty());
        assertEquals("not-an-address", page.selectFirst("#contact-email").attr("value"));
        assertEquals("My message \"quoted\" <b>x</b>", page.selectFirst("#contact-message").text());
        assertTrue(page.select("#contact-message b").isEmpty(), "the input is shown as text");
        assertTrue(outbox.sent().isEmpty());
        assertEquals("no-store", response.header("Cache-Control"));
    }

    @Test
    void whenSendingFailsTheVisitorSeesAnErrorAndKeepsTheInput() {
        outbox.failSends(true);
        Response response = submit("anna@example.org", "Hi", "Can I join?");
        assertEquals(503, response.statusCode());
        Document page = Jsoup.parse(response.asString());
        assertTrue(page.select(".alert-error").text().contains("could not be sent"));
        assertEquals("Can I join?", page.selectFirst("#contact-message").text());
    }

    @Test
    void tooManyMessagesGiveAClearMessage() {
        String visitor = "10.5.5." + (int) (Math.random() * 200);
        Response last = null;
        for (int i = 0; i < 6; i++) {
            last =
                    given().redirects()
                            .follow(false)
                            .header("Origin", TestAccounts.ORIGIN)
                            .header("X-Forwarded-For", visitor)
                            .formParam("email", "a@example.org")
                            .formParam("subject", "s")
                            .formParam("message", "m")
                            .post("/become-a-member");
        }
        assertEquals(429, last.statusCode());
        assertTrue(Jsoup.parse(last.asString()).text().contains("too many messages"));
    }

    @Test
    void aMessageFromAnotherSiteIsRefused() {
        assertEquals(
                403,
                given().redirects()
                        .follow(false)
                        .header("Origin", "https://evil.example")
                        .formParam("email", "a@example.org")
                        .formParam("subject", "s")
                        .formParam("message", "m")
                        .post("/become-a-member")
                        .statusCode());
        assertTrue(outbox.sent().isEmpty());
    }
}
