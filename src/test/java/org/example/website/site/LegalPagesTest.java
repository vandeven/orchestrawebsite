package org.example.website.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.api.users.UserSummary;
import org.example.website.platform.api.LegalConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The legal pages, the footer and the cookie notice (LEG-1 to LEG-10). */
@QuarkusTest
class LegalPagesTest {

    @Inject TestAccounts accounts;
    @Inject LegalConfig legal;

    private static Document get(String path) {
        return Jsoup.parse(given().accept("text/html").get(path).asString());
    }

    // LEG-1, LEG-10

    @ParameterizedTest
    @ValueSource(strings = {"/privacy", "/cookies", "/terms"})
    void thePagesArePublicAndCachedBriefly(String path) {
        Response response = given().accept("text/html").get(path);
        assertEquals(200, response.statusCode());
        assertEquals("public, max-age=60", response.header("Cache-Control"));
        Document page = Jsoup.parse(response.asString());
        assertTrue(
                page.selectFirst(".legal-updated")
                        .text()
                        .matches("Last updated: \\d{1,2} [A-Z][a-z]+ \\d{4}"));
    }

    @Test
    void theFooterLinksToTheThreePagesOnPublicSecureAndErrorPages() {
        for (String page : new String[] {"/", "/does-not-exist"}) {
            assertFooter(get(page));
        }
        Document secure =
                Jsoup.parse(
                        given().accept("text/html")
                                .header("Cookie", accounts.cookie(Role.MEMBER))
                                .get("/members")
                                .asString());
        assertFooter(secure);
    }

    private static void assertFooter(Document page) {
        assertEquals(
                List.of("/privacy", "/cookies", "/terms"),
                page.select(".site-footer-list a").stream().map(a -> a.attr("href")).toList());
    }

    // LEG-8

    @Test
    void theFooterShowsTheFoundationOnEveryPage() {
        for (String path : new String[] {"/", "/privacy", "/agenda", "/does-not-exist"}) {
            String footer = get(path).selectFirst(".site-footer").text();
            assertTrue(footer.contains(legal.foundationName()), path);
            assertTrue(footer.contains(legal.address()), path);
            assertTrue(footer.contains(legal.registrationNumber()), path);
            assertTrue(footer.contains(legal.contactEmail()), path);
        }
    }

    // LEG-3, LEG-4

    @Test
    void thePrivacyPageHasTheRequiredParts() {
        Document page = get("/privacy");
        String text = page.text();
        for (String needed :
                new String[] {
                    "Who is responsible",
                    "Google Cloud",
                    "Brevo",
                    "GitHub",
                    "europe-west4",
                    "not stored on the website",
                    "within one month",
                    "Autoriteit Persoonsgegevens",
                    "Your rights",
                    legal.privacyEmail()
                }) {
            assertTrue(text.contains(needed), needed);
        }
        assertNotNull(page.selectFirst("a[href='mailto:" + legal.privacyEmail() + "']"));
    }

    @Test
    void theRetentionTableMatchesTheRealSettings() throws Exception {
        Document page = get("/privacy");
        var rows = page.select("tbody tr");
        java.util.Map<String, String> table = new java.util.LinkedHashMap<>();
        for (var row : rows) {
            table.put(row.child(0).text(), row.child(1).text());
        }
        assertEquals("1 day", table.get("Login session").replace("At most ", ""));
        assertEquals("1 hour", table.get("Password recovery link"));
        assertEquals(
                legal.logRetentionDays() + " days",
                table.get("Application logs and the admin audit trail"));
        assertEquals(legal.backupRetentionDays() + " days", table.get("Database backups"));
        assertEquals(
                legal.deletedFilesDays() + " days", table.get("Deleted member files and songs"));
        assertEquals("Not stored on the website", table.get("Contact form messages"));
        assertEquals(7, rows.size());

        // The values are the ones the infrastructure really uses: backups are kept 30 days
        Path data = Path.of("infra/data.tf");
        if (Files.exists(data)) {
            String infra = Files.readString(data);
            assertTrue(
                    infra.contains("retention = \"" + legal.backupRetentionDays() * 86400 + "s\""),
                    "the backup retention in infra/data.tf is not "
                            + legal.backupRetentionDays()
                            + " days");
        }
    }

    // LEG-6

    @Test
    void everyCookieTheWebsiteSetsIsOnTheCookiesPageAndNoOtherIsSet() {
        Set<String> seen = new TreeSet<>();
        for (String path :
                new String[] {
                    "/",
                    "/become-a-member",
                    "/agenda",
                    "/privacy",
                    "/cookies",
                    "/terms",
                    "/login",
                    "/forgot-password",
                    "/does-not-exist"
                }) {
            seen.addAll(
                    given().accept("text/html").get(path).detailedCookies().asList().stream()
                            .map(c -> c.getName())
                            .toList());
        }
        // Logging in sets the session cookie
        UserSummary user = accounts.newUser(Role.MEMBER);
        Response login =
                given().redirects()
                        .follow(false)
                        .header("Origin", TestAccounts.ORIGIN)
                        .formParam("email", user.email())
                        .formParam("password", TestAccounts.PASSWORD)
                        .post("/login");
        seen.addAll(login.detailedCookies().asList().stream().map(c -> c.getName()).toList());
        // Opening a recovery link sets the recovery cookie
        given().redirects()
                .follow(false)
                .header("Origin", TestAccounts.ORIGIN)
                .header("X-Forwarded-For", TestAccounts.randomAddress())
                .formParam("email", user.email())
                .post("/forgot-password");
        String token = accounts.recoveryToken(user.email());
        assertNotNull(token);
        Response recovery =
                given().redirects()
                        .follow(false)
                        .accept("text/html")
                        .get("/reset-password?token=" + token);
        seen.addAll(recovery.detailedCookies().asList().stream().map(c -> c.getName()).toList());
        // Pages for members and admins
        for (Role role : new Role[] {Role.MEMBER, Role.ADMIN}) {
            for (String path : new String[] {"/members", "/account", "/users", "/edit"}) {
                seen.addAll(
                        given()
                                .redirects()
                                .follow(false)
                                .accept("text/html")
                                .header("Cookie", accounts.cookie(role))
                                .get(path)
                                .detailedCookies()
                                .asList()
                                .stream()
                                .map(c -> c.getName())
                                .toList());
            }
        }
        Set<String> listed = new TreeSet<>();
        for (var code : get("/cookies").select("tbody code")) {
            listed.add(code.text());
        }
        assertEquals(Set.of("__Host-session", "__Host-recovery"), listed);
        assertEquals(listed, seen, "the cookies set by the website and the cookies page differ");
    }

    @Test
    void theCookiesPageSaysWhatIsAndIsNotUsed() {
        String text = get("/cookies").text();
        assertTrue(text.contains("no tracking, advertising or analytics cookies"));
        assertTrue(text.contains("At most 1 day"));
        assertTrue(text.contains("15 minutes"));
    }

    @Test
    void theCookieNoticeIsOnEveryPageHiddenUntilTheScriptShowsIt() {
        for (String path : new String[] {"/", "/login", "/does-not-exist"}) {
            Document page = get(path);
            var notice = page.selectFirst("[data-cookie-notice]");
            assertNotNull(notice, path);
            assertTrue(notice.hasAttr("hidden"));
            assertEquals("OK", notice.selectFirst("button").text());
            assertEquals("/cookies", notice.selectFirst("a").attr("href"));
            assertNotNull(page.selectFirst("script[src='/js/cookie-notice.js']"));
        }
        assertEquals(200, given().get("/js/cookie-notice.js").statusCode());
    }

    // LEG-7, LEG-9

    @Test
    void theTermsSayWhatMembersMayDo() {
        String text = get("/terms").text();
        for (String needed :
                new String[] {
                    "for members of the orchestra", "rehearse and perform", "Do not share",
                    "personal", "allowed to use", "remove your access"
                }) {
            assertTrue(text.contains(needed), needed);
        }
    }

    @Test
    void theMemberPageRemindsMembersAndLinksToTheTerms() {
        Document page =
                Jsoup.parse(
                        given().accept("text/html")
                                .header("Cookie", accounts.cookie(Role.MEMBER))
                                .get("/members")
                                .asString());
        assertTrue(
                page.text()
                        .contains(
                                "These files are for members of the orchestra only. Please do not share them."));
        assertEquals("/terms", page.selectFirst("main a[href='/terms']").attr("href"));
        assertTrue(page.select("main input[type=checkbox]").isEmpty());
    }

    @Test
    void nothingIsLoadedFromAnotherWebsite() {
        for (String path : new String[] {"/privacy", "/cookies", "/terms"}) {
            for (var element : get(path).select("[src], link[href]")) {
                String value = element.hasAttr("src") ? element.attr("src") : element.attr("href");
                if (element.is("link[rel=canonical]")) {
                    continue;
                }
                assertFalse(
                        value.startsWith("http") || value.startsWith("//"), path + ": " + value);
            }
        }
    }
}
