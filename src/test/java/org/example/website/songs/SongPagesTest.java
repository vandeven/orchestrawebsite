package org.example.website.songs;

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
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.internal.limits.InMemoryRateLimiter;
import org.example.website.songs.api.FileType;
import org.example.website.songs.api.Song;
import org.example.website.songs.api.SongFile;
import org.example.website.songs.api.SongManagement;
import org.example.website.songs.api.UploadTicket;
import org.example.website.songs.internal.files.InMemoryFileStorage;
import org.example.website.songs.internal.files.SongFiles;
import org.example.website.songs.internal.store.memory.InMemorySongs;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The member page and the song management pages (MEM-1 to MEM-21), through the real endpoints. */
@QuarkusTest
class SongPagesTest {

    @Inject SongManagement management;
    @Inject InMemorySongs songs;
    @Inject InMemoryFileStorage storage;
    @Inject InMemoryRateLimiter limits;
    @Inject TestAccounts accounts;

    private UserSummary admin;

    @BeforeEach
    @AfterEach
    void clean() {
        songs.clear();
        storage.clear();
        limits.clear();
    }

    @BeforeEach
    void admin() {
        admin = accounts.newUser(Role.ADMIN);
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

    private static Document body(Response response) {
        return Jsoup.parse(response.asString());
    }

    private Song song(String title) {
        return management.add(title, "Notes of " + title, admin);
    }

    private SongFile file(Song song, FileType type, String name, byte[] data) {
        UploadTicket ticket =
                management.requestUpload(
                        song.id(), type, "x." + type.extension(), data.length, admin);
        storage.put("songs/" + ticket.fileId() + "." + type.extension(), data);
        return management.completeUpload(
                song.id(), ticket.fileId(), type, "x." + type.extension(), name, admin);
    }

    // MEM-1, MEM-9: access

    @ParameterizedTest
    @CsvSource({"ANONYMOUS,303", "MEMBER,200", "ADMIN,200", "MASTER,200"})
    void theMemberPageNeedsALogin(String level, int status) {
        assertEquals(status, as(level).get("/members").statusCode());
    }

    @ParameterizedTest
    @CsvSource({"ANONYMOUS,303", "MEMBER,403", "ADMIN,200", "MASTER,200"})
    void theManagementPagesAreForAdminsOnly(String level, int status) {
        Song song = song("Song");
        assertEquals(status, as(level).get("/songs/manage").statusCode());
        assertEquals(status, as(level).get("/songs/manage/" + song.id()).statusCode());
        assertEquals(status, as(level).get("/songs/manage/" + song.id() + "/delete").statusCode());
    }

    @ParameterizedTest
    @CsvSource({"ANONYMOUS", "MEMBER"})
    void othersCannotChangeAnythingEvenWithDirectRequests(String level) {
        Song song = song("Song");
        SongFile file = file(song, FileType.SHEET_MUSIC, "Part", SongFiles.pdf());
        String s = "/songs/manage/" + song.id();
        assertEquals(403, as(level).formParam("title", "x").post("/songs/manage").statusCode());
        assertEquals(403, as(level).formParam("title", "x").post(s).statusCode());
        assertEquals(403, as(level).post(s + "/archive").statusCode());
        assertEquals(403, as(level).post(s + "/unarchive").statusCode());
        assertEquals(403, as(level).formParam("confirm", "yes").post(s + "/delete").statusCode());
        assertEquals(403, json(level).body("{}").post(s + "/files/upload-url").statusCode());
        assertEquals(
                403,
                as(level)
                        .formParam("displayName", "x")
                        .post("/songs/manage/files/" + file.id() + "/rename")
                        .statusCode());
        assertEquals(
                403,
                as(level)
                        .formParam("confirm", "yes")
                        .post("/songs/manage/files/" + file.id() + "/remove")
                        .statusCode());
        assertEquals(1, management.all(admin).size());
        assertEquals(1, management.find(song.id(), admin).files().size());
    }

    // The member page (MEM-2, MEM-3, MEM-5, MEM-7)

    @Test
    void membersSeeActiveSongsWithTheirFilesGroupedByTypeAndNeverArchivedOnes() {
        Song active = song("Ave Maria");
        file(active, FileType.SHEET_MUSIC, "Soprano", SongFiles.pdf());
        file(active, FileType.AUDIO, "Full recording", SongFiles.mp3WithTag());
        file(active, FileType.LYRICS, "Words", SongFiles.lyrics());
        Song archived = song("Old song");
        management.archive(archived.id(), admin);
        Document page = body(as("MEMBER").get("/members"));
        assertEquals(1, page.select(".song").size());
        assertEquals("Ave Maria", page.selectFirst(".song h2").text());
        assertFalse(page.text().contains("Old song"));
        assertEquals(
                java.util.List.of("Sheet music", "Audio", "Lyrics"),
                page.select(".song h3").eachText());
        assertTrue(page.selectFirst(".song-notes").text().contains("Notes of Ave Maria"));
    }

    @Test
    void everyFileHasADownloadLinkAndRecordingsHaveAPlayer() {
        Song song = song("Song");
        SongFile pdf = file(song, FileType.SHEET_MUSIC, "Alto", SongFiles.pdf());
        SongFile mp3 = file(song, FileType.AUDIO, "Recording", SongFiles.mp3Frame());
        Document page = body(as("MEMBER").get("/members"));
        assertEquals(
                "/members/files/" + pdf.id() + "/download",
                page.select("a[href$='" + pdf.id() + "/download']").attr("href"));
        assertNotNull(page.selectFirst("a[href='/members/files/" + mp3.id() + "/download']"));
        var audio = page.selectFirst("audio");
        assertNotNull(audio);
        assertEquals("/members/files/" + mp3.id() + "/play", audio.attr("src"));
        assertTrue(audio.hasAttr("controls"));
        assertEquals("none", audio.attr("preload"));
        assertEquals(1, page.select("audio").size(), "only recordings get a player");
        assertEquals(1, page.select("script[src^='/js/audio-player.js?v=']").size());
    }

    @Test
    void thePageHasTheReminderNoCachingAndWorksWithoutSongs() {
        Response response = as("MEMBER").get("/members");
        Document page = body(response);
        assertTrue(
                page.text()
                        .contains(
                                "These files are for members of the orchestra only. Please do not share them."));
        assertEquals("/terms", page.selectFirst("main a[href='/terms']").attr("href"));
        assertTrue(page.text().contains("There are no songs yet"));
        assertTrue(response.header("Cache-Control").contains("no-store"));
    }

    @Test
    void changesOnTheManagementPageShowOnTheMemberPageRightAway() {
        Song song = song("Before");
        assertEquals("Before", body(as("MEMBER").get("/members")).selectFirst(".song h2").text());
        as("ADMIN")
                .formParam("title", "After")
                .formParam("notes", "")
                .post("/songs/manage/" + song.id());
        assertEquals("After", body(as("MEMBER").get("/members")).selectFirst(".song h2").text());
        as("ADMIN").post("/songs/manage/" + song.id() + "/archive");
        assertTrue(body(as("MEMBER").get("/members")).select(".song").isEmpty());
        as("ADMIN").post("/songs/manage/" + song.id() + "/unarchive");
        assertEquals(1, body(as("MEMBER").get("/members")).select(".song").size());
    }

    // Downloads and playback (MEM-4, MEM-7, MEM-8)

    @Test
    void aMemberIsSentToAShortLivedSignedAddressForDownloadAndPlayback() {
        Song song = song("Song");
        SongFile mp3 = file(song, FileType.AUDIO, "Recording", SongFiles.mp3WithTag());
        Response download = as("MEMBER").get("/members/files/" + mp3.id() + "/download");
        assertEquals(303, download.statusCode());
        assertTrue(download.header("Location").contains("disposition=attachment%3B"));
        assertTrue(download.header("Location").contains("valid=15"));
        assertEquals("no-store", download.header("Cache-Control").replace("private, ", ""));
        Response play = as("MEMBER").get("/members/files/" + mp3.id() + "/play");
        assertEquals(303, play.statusCode());
        assertTrue(play.header("Location").contains("disposition=inline%3B"));
        // Every request gives a new address: that is how the player carries on after an expiry
        assertEquals(
                303, as("MEMBER").get("/members/files/" + mp3.id() + "/play?t=12345").statusCode());
    }

    @Test
    void anonymousVisitorsAreSentToLoginAndOthersSeeNothingOfAnArchivedSongOrAnUnknownFile() {
        Song song = song("Song");
        SongFile file = file(song, FileType.SHEET_MUSIC, "Part", SongFiles.pdf());
        assertEquals(
                303, as("ANONYMOUS").get("/members/files/" + file.id() + "/download").statusCode());
        assertTrue(
                as("ANONYMOUS")
                        .get("/members/files/" + file.id() + "/download")
                        .header("Location")
                        .contains("/login"));
        management.archive(song.id(), admin);
        assertEquals(
                404, as("MEMBER").get("/members/files/" + file.id() + "/download").statusCode());
        assertEquals(404, as("MEMBER").get("/members/files/" + file.id() + "/play").statusCode());
        assertEquals(
                404,
                as("MEMBER").get("/members/files/" + UUID.randomUUID() + "/download").statusCode());
        assertEquals(404, as("MEMBER").get("/members/files/not-an-id/download").statusCode());
        assertEquals(
                303, as("ADMIN").get("/members/files/" + file.id() + "/download").statusCode());
    }

    // The overview (MEM-10 to MEM-13)

    @Test
    void theOverviewSeparatesActiveFromArchivedSongs() {
        song("Active one");
        Song archived = song("Archived one");
        management.archive(archived.id(), admin);
        Document page = body(as("ADMIN").get("/songs/manage"));
        assertTrue(page.selectFirst("#active-heading").text().contains("(1)"));
        assertTrue(page.selectFirst("#archived-heading").text().contains("(1)"));
        assertTrue(page.select("form[action$='/archive']").size() == 1);
        assertTrue(page.select("form[action$='/unarchive']").size() == 1);
        assertNotNull(page.selectFirst("a[href='/songs/manage/" + archived.id() + "/delete']"));
        assertTrue(
                page.select("a[href$='/delete']").size() == 1,
                "only archived songs can be deleted from the list");
    }

    @Test
    void addingASongStartsActiveAndTheTitleRulesApplyOnTheServer() {
        Response added =
                as("ADMIN")
                        .formParam("title", "  New song  ")
                        .formParam("notes", "n")
                        .post("/songs/manage");
        assertEquals(303, added.statusCode());
        assertTrue(added.header("Location").contains("?notice=added"));
        assertEquals("New song", management.all(admin).get(0).song().title());
        assertFalse(management.all(admin).get(0).song().archived());

        for (String title : new String[] {"", "   ", "t".repeat(201), "two\nlines"}) {
            Response refused =
                    as("ADMIN")
                            .formParam("title", title)
                            .formParam("notes", "")
                            .post("/songs/manage");
            assertEquals(400, refused.statusCode(), title);
            Document page = body(refused);
            assertFalse(page.select(".alert-error").isEmpty());
            assertEquals(
                    title,
                    page.selectFirst("#song-title").attr("value").isEmpty()
                            ? title
                            : page.selectFirst("#song-title").attr("value"));
        }
        assertEquals(1, management.all(admin).size());
        assertEquals(
                303,
                as("ADMIN")
                        .formParam("title", "t".repeat(200))
                        .formParam("notes", "")
                        .post("/songs/manage")
                        .statusCode());
    }

    @Test
    void editingKeepsTheInputWhenARuleIsBroken() {
        Song song = song("Song");
        Response refused =
                as("ADMIN")
                        .formParam("title", "")
                        .formParam("notes", "My new notes")
                        .post("/songs/manage/" + song.id());
        assertEquals(400, refused.statusCode());
        Document page = body(refused);
        assertEquals("My new notes", page.selectFirst("#song-notes").text());
        assertEquals("Song", management.find(song.id(), admin).song().title());
    }

    @Test
    void archivingIsOneClickAndDeletingAsksFirstAndNamesTheSong() {
        Song song = song("Ave Maria");
        file(song, FileType.AUDIO, "Recording", SongFiles.mp3WithTag());
        assertEquals(303, as("ADMIN").post("/songs/manage/" + song.id() + "/archive").statusCode());
        assertTrue(management.find(song.id(), admin).song().archived());

        Document confirm = body(as("ADMIN").get("/songs/manage/" + song.id() + "/delete"));
        assertTrue(confirm.text().contains("Ave Maria"));
        assertTrue(confirm.text().contains("cannot be undone"));
        assertTrue(confirm.text().contains("1 file"));
        assertEquals(
                "/songs/manage/" + song.id() + "/delete",
                confirm.selectFirst("main form").attr("action"));
        assertEquals(1, storage.count(), "looking at the page deletes nothing");

        assertEquals(400, as("ADMIN").post("/songs/manage/" + song.id() + "/delete").statusCode());
        assertEquals(
                400,
                as("ADMIN")
                        .formParam("confirm", "no")
                        .post("/songs/manage/" + song.id() + "/delete")
                        .statusCode());
        assertEquals(1, storage.count());
        Response deleted =
                as("ADMIN")
                        .formParam("confirm", "yes")
                        .post("/songs/manage/" + song.id() + "/delete");
        assertEquals(303, deleted.statusCode());
        assertEquals(0, storage.count());
        assertTrue(management.all(admin).isEmpty());
    }

    // Files (MEM-14 to MEM-21)

    @Test
    void theUploadRequestsAddAFileToActiveAndArchivedSongs() {
        Song song = song("Song");
        for (boolean archive : new boolean[] {false, true}) {
            if (archive) {
                management.archive(song.id(), admin);
            }
            Response ticket =
                    json("ADMIN")
                            .body(
                                    "{\"type\":\"sheet-music\",\"originalName\":\"Part.PDF\",\"size\":"
                                            + SongFiles.pdf().length
                                            + "}")
                            .post("/songs/manage/" + song.id() + "/files/upload-url");
            assertEquals(200, ticket.statusCode(), ticket.asString());
            String fileId = ticket.jsonPath().getString("fileId");
            assertEquals("application/pdf", ticket.jsonPath().getString("headers.'Content-Type'"));
            assertEquals(
                    "1,15728640",
                    ticket.jsonPath().getString("headers.'x-goog-content-length-range'"));
            storage.put("songs/" + fileId + ".pdf", SongFiles.pdf());
            Response done =
                    json("ADMIN")
                            .body(
                                    "{\"type\":\"sheet-music\",\"originalName\":\"Part.PDF\",\"displayName\":\"Soprano\"}")
                            .post("/songs/manage/" + song.id() + "/files/" + fileId + "/complete");
            assertEquals(200, done.statusCode(), done.asString());
        }
        assertEquals(2, management.find(song.id(), admin).files().size());
    }

    @Test
    void wrongTypesSizesAndContentGetAClearMessage() {
        Song song = song("Song");
        String s = "/songs/manage/" + song.id() + "/files/upload-url";
        for (String bad :
                new String[] {
                    "{\"type\":\"video\",\"originalName\":\"a.mp4\",\"size\":10}",
                    "{\"type\":\"audio\",\"originalName\":\"a.wav\",\"size\":10}",
                    "{\"type\":\"audio\",\"originalName\":\"a.mp3\",\"size\":15728641}",
                    "{\"type\":\"audio\",\"originalName\":\"a.mp3\",\"size\":0}",
                    "{}",
                    "not json"
                }) {
            Response response = json("ADMIN").body(bad).post(s);
            assertEquals(400, response.statusCode(), bad);
            assertFalse(response.jsonPath().getString("error").isBlank(), bad);
        }
        // A PDF-named file that is not a PDF is refused after the upload and deleted
        String fileId =
                json("ADMIN")
                        .body(
                                "{\"type\":\"sheet-music\",\"originalName\":\"fake.pdf\",\"size\":20}")
                        .post(s)
                        .jsonPath()
                        .getString("fileId");
        storage.put("songs/" + fileId + ".pdf", "this is not a pdf at all".getBytes());
        Response done =
                json("ADMIN")
                        .body(
                                "{\"type\":\"sheet-music\",\"originalName\":\"fake.pdf\",\"displayName\":\"Fake\"}")
                        .post("/songs/manage/" + song.id() + "/files/" + fileId + "/complete");
        assertEquals(400, done.statusCode());
        assertFalse(storage.exists("songs/" + fileId + ".pdf"));
    }

    @Test
    void renamingChangesOnlyTheNameAndRemovingAsksFirstAndNamesTheFileAndTheSong() {
        Song song = song("Ave Maria");
        SongFile file = file(song, FileType.SHEET_MUSIC, "Old name", SongFiles.pdf());
        assertEquals(
                303,
                as("ADMIN")
                        .formParam("displayName", "Tenor 2")
                        .post("/songs/manage/files/" + file.id() + "/rename")
                        .statusCode());
        assertEquals("Tenor 2", management.find(song.id(), admin).files().get(0).displayName());
        Response refused =
                as("ADMIN")
                        .formParam("displayName", "")
                        .post("/songs/manage/files/" + file.id() + "/rename");
        assertEquals(400, refused.statusCode());

        Document confirm = body(as("ADMIN").get("/songs/manage/files/" + file.id() + "/remove"));
        assertTrue(confirm.text().contains("Tenor 2") && confirm.text().contains("Ave Maria"));
        assertEquals(
                400, as("ADMIN").post("/songs/manage/files/" + file.id() + "/remove").statusCode());
        assertEquals(1, storage.count());
        assertEquals(
                303,
                as("ADMIN")
                        .formParam("confirm", "yes")
                        .post("/songs/manage/files/" + file.id() + "/remove")
                        .statusCode());
        assertEquals(0, storage.count());
        assertTrue(management.find(song.id(), admin).files().isEmpty());
    }

    @Test
    void theSongPageShowsFilesAFormToUploadAndNamesEverythingForScreenReaders() {
        Song song = song("Song");
        SongFile file = file(song, FileType.AUDIO, "Recording", SongFiles.mp3WithTag());
        Document page = body(as("ADMIN").get("/songs/manage/" + song.id()));
        assertEquals("Song", page.selectFirst("h1").text());
        assertNotNull(page.selectFirst("label[for='name-" + file.id() + "']"));
        assertNotNull(page.selectFirst("form[data-song-upload]"));
        assertTrue(
                page.selectFirst("form[data-song-upload]").hasAttr("hidden"),
                "shown by the script");
        assertEquals(3, page.select("#upload-type option").size());
        assertTrue(page.text().contains("needs JavaScript"));
        assertEquals(1, page.select("script[src^='/js/song-upload.js?v=']").size());
        assertTrue(page.text().contains("Only add files the orchestra is allowed to share"));
    }

    @Test
    void badIdsAreNotFoundAndRequestsFromAnotherSiteAreRefused() {
        assertEquals(404, as("ADMIN").get("/songs/manage/not-an-id").statusCode());
        assertEquals(404, as("ADMIN").get("/songs/manage/" + UUID.randomUUID()).statusCode());
        assertEquals(404, as("ADMIN").get("/songs/manage/files/not-an-id/remove").statusCode());
        assertEquals(
                403,
                given().redirects()
                        .follow(false)
                        .header("Cookie", accounts.cookie(Role.ADMIN))
                        .header("Origin", "https://evil.example")
                        .formParam("title", "x")
                        .post("/songs/manage")
                        .statusCode());
    }

    @Test
    void theCacheIsNeverUsedForSongPages() {
        Song song = song("Song");
        for (String path :
                new String[] {"/songs/manage", "/songs/manage/" + song.id(), "/members"}) {
            String cache = as("ADMIN").get(path).header("Cache-Control");
            assertTrue(cache.contains("no-store"), path + ": " + cache);
        }
    }
}
