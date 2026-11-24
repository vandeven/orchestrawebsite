package org.example.website.content;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.users.Role;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The edit pages (EDT-1, EDT-2, EDT-5 to EDT-8, EDT-18) for admins and the master. */
@QuarkusTest
class EditPagesTest {

    @Inject TestAccounts accounts;
    @Inject EditableTexts texts;

    @AfterEach
    void clearTheTexts() {
        for (TextKey key : TextKey.values()) {
            texts.clear(key, accounts.newUser(Role.ADMIN));
        }
    }

    private RequestSpecification as(String level) {
        RequestSpecification spec =
                given().redirects()
                        .follow(false)
                        .accept("text/html")
                        .header("Origin", TestAccounts.ORIGIN);
        return level.equals("ANONYMOUS")
                ? spec
                : spec.header("Cookie", accounts.cookie(Role.valueOf(level)));
    }

    private static Document body(Response response) {
        return Jsoup.parse(response.asString());
    }

    // EDT-1: only admins and the master

    @ParameterizedTest
    @CsvSource({
        "ANONYMOUS,303",
        "MEMBER,403",
        "ADMIN,200",
        "MASTER,200",
    })
    void onlyAdminsAndTheMasterCanOpenTheList(String level, int status) {
        assertEquals(status, as(level).get("/edit").statusCode());
        assertEquals(status, as(level).get("/edit/about").statusCode());
    }

    @ParameterizedTest
    @CsvSource({
        "ANONYMOUS,403",
        "MEMBER,403"
    }) // a state-changing request is refused, not redirected
    void othersCannotSaveEvenWhenTheySendTheRequestDirectly(String level, int status) {
        for (String action : new String[] {"save", "preview"}) {
            assertEquals(
                    status,
                    as(level)
                            .contentType("text/plain")
                            .body("html=" + "<p>x</p>")
                            .post("/edit/about/" + action)
                            .statusCode());
        }
        assertEquals(
                status,
                as(level).formParam("confirm", "yes").post("/edit/about/clear").statusCode());
        assertTrue(texts.get(TextKey.ABOUT).isEmpty());
    }

    // EDT-2: the list

    @Test
    void theListShowsEveryTextWithItsPageAndLastChange() {
        texts.save(TextKey.AGENDA, "<p>x</p>", accounts.newUser(Role.ADMIN));
        Document page = body(as("ADMIN").get("/edit"));
        assertEquals(4, page.select("tbody tr").size());
        String text = page.select("tbody").text();
        assertTrue(text.contains("About page text") && text.contains("Audition text"));
        assertTrue(text.contains("Never changed"));
        assertFalse(page.select("tbody tr").get(3).text().contains("Never changed"));
        assertEquals("/edit/about", page.selectFirst("tbody a").attr("href"));
    }

    @Test
    void anUnknownTextIsNotFound() {
        for (String key : new String[] {"nope", "ABOUT", "about%2F..", "%2e%2e"}) {
            assertEquals(404, as("ADMIN").get("/edit/" + key).statusCode(), key);
        }
        assertEquals(
                404,
                as("ADMIN")
                        .contentType("text/plain")
                        .body("html=" + "x")
                        .post("/edit/nope/save")
                        .statusCode());
    }

    // EDT-3 to EDT-5: the editor

    @Test
    void theEditorShowsTheStoredTextInAnHtmlFieldAndLoadsTheScriptOnlyHere() {
        texts.save(TextKey.ABOUT, "<p>Hello <b>you</b></p>", accounts.newUser(Role.ADMIN));
        Document page = body(as("ADMIN").get("/edit/about"));
        assertEquals("<p>Hello <b>you</b></p>", page.selectFirst("textarea[name=html]").text());
        assertEquals(1, page.select("script[src^='/js/editor.js?v=']").size());
        assertTrue(
                body(as("ADMIN").get("/edit")).select("script[src^='/js/editor.js?v=']").isEmpty());
        assertTrue(body(given().get("/")).select("script[src^='/js/editor.js?v=']").isEmpty());
    }

    @Test
    void textInTheEditorFieldIsNeverRunAsMarkup() {
        // Stored texts are cleaned, but the field must show HTML as text in any case
        Document page =
                body(
                        as("ADMIN")
                                .contentType("text/plain")
                                .body("html=" + "</textarea><script>alert(1)</script>")
                                .post("/edit/about"));
        assertTrue(page.select("script:not([src])").isEmpty());
        assertEquals(
                "</textarea><script>alert(1)</script>",
                page.selectFirst("textarea[name=html]").text());
    }

    // EDT-7: the preview stores nothing

    @Test
    void thePreviewShowsTheCleanedTextAndStoresNothing() {
        Document page =
                body(
                        as("ADMIN")
                                .contentType("text/plain")
                                .body("html=" + "<h2>Hi</h2><script>alert(1)</script>")
                                .post("/edit/about/preview"));
        assertEquals("Hi", page.selectFirst(".page-text h2").text());
        assertTrue(page.select(".page-text script").isEmpty());
        assertEquals("About", page.selectFirst("h1").text());
        assertTrue(texts.get(TextKey.ABOUT).isEmpty());
        // Save and back to editing keep what was typed
        assertEquals(
                "<h2>Hi</h2><script>alert(1)</script>",
                page.selectFirst("input[name=html]").attr("value"));
    }

    @Test
    void backToEditingKeepsWhatWasTyped() {
        Document page =
                body(
                        as("ADMIN")
                                .contentType("text/plain")
                                .body("html=" + "<p>draft</p>")
                                .post("/edit/about"));
        assertEquals("<p>draft</p>", page.selectFirst("textarea").text());
    }

    // EDT-6, EDT-8: saving cleans on the server

    @Test
    void savingCleansTheTextAndShowsExactlyWhatIsStored() {
        Response saved =
                as("ADMIN")
                        .contentType("text/plain")
                        .body("html=<p>Hello</p><script>alert(1)</script><p onclick=\"x()\">b</p>")
                        .post("/edit/about/save");
        assertEquals(303, saved.statusCode());
        assertEquals(
                "/edit/about?notice=saved",
                java.net.URI.create(saved.header("Location")).toString());
        assertEquals("<p>Hello</p><p>b</p>", texts.get(TextKey.ABOUT).html());
        Document page = body(as("ADMIN").get("/edit/about?notice=saved"));
        assertEquals("<p>Hello</p><p>b</p>", page.selectFirst("textarea").text());
        assertTrue(page.select(".alert-success").text().contains("Saved"));
        assertEquals("private, no-store", saved.header("Cache-Control"));
    }

    @Test
    void anUnknownNoticeIsIgnoredAndNeverShownAsText() {
        Document page =
                body(as("ADMIN").get("/edit/about?notice=%3Cscript%3Ealert(1)%3C/script%3E"));
        assertTrue(page.select(".alert").isEmpty());
    }

    @Test
    void aTextOverTwoHundredKilobytesIsRefusedAndTheOldTextStays() {
        texts.save(TextKey.ABOUT, "<p>old</p>", accounts.newUser(Role.ADMIN));
        Response response =
                as("ADMIN")
                        .contentType("text/plain")
                        .body("html=" + "<p>" + "a".repeat(210 * 1024) + "</p>")
                        .post("/edit/about/save");
        assertEquals(400, response.statusCode());
        assertTrue(body(response).select(".alert-error").text().contains("too long"));
        assertEquals("<p>old</p>", texts.get(TextKey.ABOUT).html());
    }

    @Test
    void aLongTextIsAcceptedAndABodyWithoutTheTextFieldIsRefused() {
        String longText = "<p>" + "word ".repeat(20_000) + "</p>"; // about 100 KB, far over 2 KB
        // A browser ends the line of a plain text form with CRLF
        Response saved =
                as("ADMIN")
                        .contentType("text/plain")
                        .body("html=" + longText + "\r\n")
                        .post("/edit/about/save");
        assertEquals(303, saved.statusCode());
        assertEquals(longText.length(), texts.get(TextKey.ABOUT).html().length());
        assertEquals(
                400,
                as("ADMIN")
                        .contentType("text/plain")
                        .body("other=<p>x</p>")
                        .post("/edit/about/save")
                        .statusCode());
        assertEquals(
                400, as("ADMIN").contentType("text/plain").post("/edit/about/save").statusCode());
    }

    @Test
    void aRequestFromAnotherSiteIsRefused() {
        assertEquals(
                403,
                given().redirects()
                        .follow(false)
                        .header("Cookie", accounts.cookie(Role.ADMIN))
                        .header("Origin", "https://evil.example")
                        .contentType("text/plain")
                        .body("html=" + "<p>pwned</p>")
                        .post("/edit/about/save")
                        .statusCode());
        assertTrue(texts.get(TextKey.ABOUT).isEmpty());
    }

    // EDT-18: clearing needs a confirmation

    @Test
    void savingAnEmptyTextAsksForConfirmationAndChangesNothingYet() {
        texts.save(TextKey.AUDITION, "<p>Auditions</p>", accounts.newUser(Role.ADMIN));
        for (String empty :
                new String[] {"", "   ", "<script>alert(1)</script>", "<p></p>x".substring(0, 0)}) {
            Response response =
                    as("ADMIN")
                            .contentType("text/plain")
                            .body("html=" + empty)
                            .post("/edit/audition/save");
            assertEquals(200, response.statusCode());
            Document page = body(response);
            assertTrue(page.text().contains("Audition text"));
            assertTrue(page.text().contains("hidden"), "says what happens on the page");
            assertTrue(page.text().contains("No earlier version is kept"));
            assertEquals("/edit/audition/clear", page.selectFirst("main form").attr("action"));
            assertEquals("<p>Auditions</p>", texts.get(TextKey.AUDITION).html());
        }
    }

    @Test
    void clearingWorksOnlyWithTheConfirmationField() {
        texts.save(TextKey.AUDITION, "<p>Auditions</p>", accounts.newUser(Role.ADMIN));
        assertEquals(400, as("ADMIN").post("/edit/audition/clear").statusCode());
        assertEquals(
                400,
                as("ADMIN").formParam("confirm", "no").post("/edit/audition/clear").statusCode());
        assertEquals(405, as("ADMIN").get("/edit/audition/clear").statusCode());
        assertEquals("<p>Auditions</p>", texts.get(TextKey.AUDITION).html());

        Response cleared = as("ADMIN").formParam("confirm", "yes").post("/edit/audition/clear");
        assertEquals(303, cleared.statusCode());
        assertTrue(cleared.header("Location").endsWith("/edit?notice=cleared"));
        assertTrue(texts.get(TextKey.AUDITION).isEmpty());
    }

    // EDT-8: the public page shows it

    @Test
    void aSavedTextAppearsOnThePublicPage() {
        as("ADMIN")
                .contentType("text/plain")
                .body("html=" + "<h2>Our band</h2>")
                .post("/edit/about/save");
        assertEquals(
                "Our band",
                Jsoup.parse(given().get("/").asString()).selectFirst(".page-text h2").text());
    }

    @Test
    void theEditorKnowsTheImageStorageAndTheLibraryAndLoadsTheUploadScriptFirst() {
        var library = org.example.website.content.api.images.ImageLibrary.class;
        Document page = body(as("ADMIN").get("/edit/about"));
        assertEquals(
                "https://images.example.test/",
                page.selectFirst("form[data-editor]").attr("data-image-base"));
        assertNotNull(page.selectFirst("ul[data-library]"));
        var scripts = page.select("script[src]").stream().map(e -> e.attr("src")).toList();
        int upload = indexOf(scripts, "/js/image-upload.js?v=");
        int editor = indexOf(scripts, "/js/editor.js?v=");
        assertTrue(upload >= 0 && editor > upload, scripts.toString());
        assertNotNull(library);
    }

    private static int indexOf(java.util.List<String> scripts, String start) {
        for (int i = 0; i < scripts.size(); i++) {
            if (scripts.get(i).startsWith(start)) {
                return i;
            }
        }
        return -1;
    }
}
