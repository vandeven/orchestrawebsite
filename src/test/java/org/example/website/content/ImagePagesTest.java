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
import java.util.UUID;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.users.Role;
import org.example.website.content.api.images.ImageLibrary;
import org.example.website.content.api.texts.EditableTexts;
import org.example.website.content.api.texts.TextKey;
import org.example.website.content.internal.images.ImageFiles;
import org.example.website.content.internal.images.InMemoryImageStorage;
import org.example.website.content.internal.store.memory.InMemoryImages;
import org.example.website.platform.internal.limits.InMemoryRateLimiter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The image library pages and the two requests of an upload (EDT-9 to EDT-17). */
@QuarkusTest
class ImagePagesTest {

    @Inject InMemoryImageStorage storage;
    @Inject InMemoryImages images;
    @Inject InMemoryRateLimiter limits;
    @Inject EditableTexts texts;
    @Inject ImageLibrary library;
    @Inject TestAccounts accounts;

    @BeforeEach
    @AfterEach
    void clean() {
        storage.clear();
        images.clear();
        limits.clear();
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

    private RequestSpecification json(String level) {
        return as(level).accept("application/json").contentType("application/json");
    }

    /** What the browser does, through the real endpoints. */
    private String addImage(byte[] file, String type, String alt, boolean decorative) {
        Response ticket =
                json("ADMIN")
                        .body("{\"contentType\":\"" + type + "\",\"size\":" + file.length + "}")
                        .post("/edit/images/upload-url");
        assertEquals(200, ticket.statusCode(), ticket.asString());
        String id = ticket.jsonPath().getString("id");
        String extension =
                type.equals("image/png") ? "png" : type.equals("image/jpeg") ? "jpg" : "webp";
        storage.put(id + "." + extension, file);
        Response done =
                json("ADMIN")
                        .body(
                                "{\"contentType\":\""
                                        + type
                                        + "\",\"alt\":\""
                                        + alt
                                        + "\",\"decorative\":"
                                        + decorative
                                        + "}")
                        .post("/edit/images/" + id + "/complete");
        assertEquals(200, done.statusCode(), done.asString());
        return id;
    }

    // EDT-1: only admins and the master

    @ParameterizedTest
    @CsvSource({"ANONYMOUS,303", "MEMBER,403", "ADMIN,200", "MASTER,200"})
    void onlyAdminsAndTheMasterOpenTheLibrary(String level, int status) {
        assertEquals(status, as(level).get("/edit/images").statusCode());
    }

    @ParameterizedTest
    @CsvSource({"ANONYMOUS", "MEMBER"})
    void othersCannotUploadOrDeleteEvenWithDirectRequests(String level) {
        String id = UUID.randomUUID().toString();
        assertEquals(
                403,
                json(level)
                        .body("{\"contentType\":\"image/png\",\"size\":10}")
                        .post("/edit/images/upload-url")
                        .statusCode());
        assertEquals(
                403, json(level).body("{}").post("/edit/images/" + id + "/complete").statusCode());
        assertEquals(
                403,
                as(level)
                        .formParam("confirm", "yes")
                        .post("/edit/images/" + id + "/delete")
                        .statusCode());
    }

    // The upload requests

    @Test
    void anAdminGetsASignedAddressAndTheHeadersToSend() {
        Response response =
                json("ADMIN")
                        .body("{\"contentType\":\"image/webp\",\"size\":123456}")
                        .post("/edit/images/upload-url");
        assertEquals(200, response.statusCode());
        assertNotNull(UUID.fromString(response.jsonPath().getString("id")));
        assertTrue(response.jsonPath().getString("uploadUrl").startsWith("https://"));
        assertEquals("image/webp", response.jsonPath().getString("headers.'Content-Type'"));
        assertEquals("no-store", response.header("Cache-Control").replace("private, ", ""));
    }

    @Test
    void aWrongTypeOrSizeIsRefusedWithAMessageForPeople() {
        for (String body :
                new String[] {
                    "{\"contentType\":\"image/svg+xml\",\"size\":100}",
                    "{\"contentType\":\"image/png\",\"size\":0}",
                    "{\"contentType\":\"image/png\",\"size\":10485761}",
                    "{\"contentType\":\"image/png\"}",
                    "{}",
                    "not json at all"
                }) {
            Response response = json("ADMIN").body(body).post("/edit/images/upload-url");
            assertEquals(400, response.statusCode(), body);
            assertFalse(response.jsonPath().getString("error").isBlank(), body);
        }
    }

    @Test
    void aWholeUploadAddsTheImageAndShowsItInTheLibrary() {
        String id = addImage(ImageFiles.png(40, 30), "image/png", "The choir singing", false);
        Document page = Jsoup.parse(as("ADMIN").get("/edit/images").asString());
        var card = page.selectFirst(".image-card");
        assertNotNull(card);
        assertEquals("The choir singing", card.selectFirst("img").attr("alt"));
        assertEquals(
                "https://images.example.test/" + id + ".png", card.selectFirst("img").attr("src"));
        assertEquals("40", card.selectFirst("img").attr("width"));
        assertTrue(card.text().contains("40 × 30"));
        assertTrue(card.text().contains("Unused"));
        assertEquals("/edit/images/" + id + "/delete", card.selectFirst("a").attr("href"));
    }

    @Test
    void aFileThatIsNotAnImageIsRefusedAndRemovedFromStorage() {
        Response ticket =
                json("ADMIN")
                        .body("{\"contentType\":\"image/png\",\"size\":60}")
                        .post("/edit/images/upload-url");
        String id = ticket.jsonPath().getString("id");
        storage.put(id + ".png", ImageFiles.pdf());
        Response done =
                json("ADMIN")
                        .body("{\"contentType\":\"image/png\",\"alt\":\"x\"}")
                        .post("/edit/images/" + id + "/complete");
        assertEquals(400, done.statusCode());
        assertFalse(storage.exists(id + ".png"));
        assertTrue(library.list().isEmpty());
    }

    @Test
    void theDescriptionIsRequiredExceptForDecorativeImages() {
        Response ticket =
                json("ADMIN")
                        .body("{\"contentType\":\"image/png\",\"size\":60}")
                        .post("/edit/images/upload-url");
        String id = ticket.jsonPath().getString("id");
        storage.put(id + ".png", ImageFiles.png(10, 10));
        Response missing =
                json("ADMIN")
                        .body("{\"contentType\":\"image/png\",\"alt\":\"\"}")
                        .post("/edit/images/" + id + "/complete");
        assertEquals(400, missing.statusCode());
        assertTrue(missing.jsonPath().getString("error").contains("description"));
        Response decorative =
                json("ADMIN")
                        .body("{\"contentType\":\"image/png\",\"decorative\":true}")
                        .post("/edit/images/" + id + "/complete");
        assertEquals(200, decorative.statusCode());
        assertEquals("", decorative.jsonPath().getString("alt"));
        assertTrue(
                Jsoup.parse(as("ADMIN").get("/edit/images").asString())
                        .text()
                        .contains("Decorative"));
    }

    @Test
    void anUploadRequestFromAnotherSiteIsRefused() {
        assertEquals(
                403,
                given().redirects()
                        .follow(false)
                        .header("Cookie", accounts.cookie(Role.ADMIN))
                        .header("Origin", "https://evil.example")
                        .contentType("application/json")
                        .body("{\"contentType\":\"image/png\",\"size\":10}")
                        .post("/edit/images/upload-url")
                        .statusCode());
    }

    // The page itself

    @Test
    void theUploadFormHasLabelsTheReminderAndNeedsJavaScript() {
        Document page = Jsoup.parse(as("ADMIN").get("/edit/images").asString());
        for (String id : new String[] {"image-file", "image-alt", "image-decorative"}) {
            assertNotNull(page.selectFirst("label[for=" + id + "]"), id);
        }
        assertEquals(
                "image/jpeg,image/png,image/webp", page.selectFirst("#image-file").attr("accept"));
        assertTrue(page.text().contains("Only upload photos of people with their permission"));
        assertTrue(
                page.selectFirst("form[data-image-upload]").hasAttr("hidden"),
                "shown by the script");
        assertTrue(page.text().contains("needs JavaScript"));
        assertEquals(1, page.select("script[src^='/js/image-upload.js?v=']").size());
        assertEquals(
                "/edit/images",
                Jsoup.parse(as("ADMIN").get("/edit").asString())
                        .selectFirst("a[href='/edit/images']")
                        .attr("href"));
    }

    @Test
    void anEmptyLibraryAndAnUnknownNoticeAreHandled() {
        Document page = Jsoup.parse(as("ADMIN").get("/edit/images?notice=%3Cb%3Ex").asString());
        assertTrue(page.text().contains("There are no images yet"));
        assertTrue(page.select(".alert-success").isEmpty());
    }

    // Deleting (EDT-16)

    @Test
    void deletingAsksFirstAndNamesTheTextsThatUseTheImage() {
        String id = addImage(ImageFiles.png(10, 10), "image/png", "x", false);
        String url = library.find(UUID.fromString(id)).orElseThrow().image().url();
        texts.save(
                TextKey.AGENDA,
                "<p><img src=\"" + url + "\" alt=\"x\"></p>",
                accounts.newUser(Role.ADMIN));
        Document page = Jsoup.parse(as("ADMIN").get("/edit/images/" + id + "/delete").asString());
        assertTrue(page.text().contains("cannot be restored"));
        assertTrue(page.select(".alert-warning").text().contains("Agenda text"));
        assertEquals(
                "/edit/images/" + id + "/delete", page.selectFirst("main form").attr("action"));
        assertTrue(library.find(UUID.fromString(id)).isPresent(), "nothing is deleted by looking");
    }

    @Test
    void deletingNeedsTheConfirmationFieldAndRemovesTheImageForGood() {
        String id = addImage(ImageFiles.png(10, 10), "image/png", "x", false);
        assertEquals(400, as("ADMIN").post("/edit/images/" + id + "/delete").statusCode());
        assertEquals(
                400,
                as("ADMIN")
                        .formParam("confirm", "no")
                        .post("/edit/images/" + id + "/delete")
                        .statusCode());
        assertEquals(
                405,
                as("ADMIN").get("/edit/images/" + id + "/delete").statusCode() == 200 ? 405 : 0);
        assertTrue(storage.exists(id + ".png"));

        Response deleted =
                as("ADMIN").formParam("confirm", "yes").post("/edit/images/" + id + "/delete");
        assertEquals(303, deleted.statusCode());
        assertTrue(deleted.header("Location").endsWith("/edit/images?notice=deleted"));
        assertFalse(storage.exists(id + ".png"));
        assertTrue(library.list().isEmpty());
        assertEquals(404, as("ADMIN").get("/edit/images/" + id + "/delete").statusCode());
    }

    @Test
    void anImageIdThatIsNotAnIdIsNotFound() {
        assertEquals(404, as("ADMIN").get("/edit/images/not-an-id/delete").statusCode());
        assertEquals(
                404,
                as("ADMIN")
                        .formParam("confirm", "yes")
                        .post("/edit/images/not-an-id/delete")
                        .statusCode());
    }
}
