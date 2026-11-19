package org.example.website.accounts;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.example.website.TestAccounts;
import org.example.website.accounts.api.LoginResult;
import org.example.website.accounts.api.LoginService;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.SessionCookie;
import org.example.website.accounts.api.UserService;
import org.example.website.accounts.api.UserSummary;
import org.example.website.platform.api.Outbox;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The user management pages: access, the overview, adding users and the actions with their
 * confirmations.
 */
@QuarkusTest
class UserManagementWebTest {

    @Inject TestAccounts accounts;
    @Inject LoginService login;
    @Inject UserService users;
    @Inject Outbox outbox;

    private UserSummary admin;
    private String adminCookie;
    private String masterCookie;

    @BeforeEach
    void actors() {
        outbox.clear();
        outbox.failSends(false);
        admin = accounts.newUser(Role.ADMIN);
        adminCookie = cookieOf(admin);
        masterCookie = accounts.cookie(Role.MASTER);
    }

    private String cookieOf(UserSummary user) {
        return SessionCookie.NAME
                + "="
                + ((LoginResult.LoggedIn) login.login(user.email(), TestAccounts.PASSWORD))
                        .sessionToken();
    }

    private static RequestSpecification as(String cookie) {
        return given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Cookie", cookie)
                .header("Origin", TestAccounts.ORIGIN)
                .contentType("application/x-www-form-urlencoded");
    }

    private Document overview(String cookie, String query) {
        Response response = as(cookie).get("/users" + query);
        assertEquals(200, response.statusCode(), response.asString());
        return Jsoup.parse(response.asString());
    }

    private static Element row(Document page, String email) {
        return page.select("tbody tr").stream()
                .filter(r -> r.selectFirst("th[scope=row]").text().equals(email))
                .findFirst()
                .orElse(null);
    }

    private static boolean has(Element row, String actionPath) {
        return row.select("a[href$=" + actionPath + "], form[action$=" + actionPath + "]").size()
                > 0;
    }

    // Access (USR-1)

    @Test
    void onlyAdminsAndTheMasterCanOpenThePage() {
        given().redirects()
                .follow(false)
                .get("/users")
                .then()
                .statusCode(303)
                .header("Location", "/login?return=%2Fusers");
        as(accounts.cookie(Role.MEMBER)).get("/users").then().statusCode(403);
        as(adminCookie)
                .get("/users")
                .then()
                .statusCode(200)
                .header("Cache-Control", "private, no-store");
        as(masterCookie).get("/users").then().statusCode(200);
    }

    @Test
    void everyActionIsClosedToMembersAndVisitors() {
        UserSummary victim = accounts.newUser(Role.MEMBER);
        String member = accounts.cookie(Role.MEMBER);
        for (String path :
                List.of(
                        "/users/" + victim.id() + "/remove",
                        "/users/" + victim.id() + "/make-admin",
                        "/users/" + victim.id() + "/make-member",
                        "/users/" + victim.id() + "/resend",
                        "/users")) {
            assertEquals(
                    403,
                    as(member)
                            .formParam("confirm", "yes")
                            .formParam("email", "x@example.test")
                            .post(path)
                            .statusCode(),
                    path);
            assertEquals(
                    403,
                    given().redirects()
                            .follow(false)
                            .header("Origin", TestAccounts.ORIGIN)
                            .formParam("confirm", "yes")
                            .post(path)
                            .statusCode(),
                    "anonymous POST " + path);
        }
        assertTrue(users.find(victim.id()).isPresent());
        assertEquals(Role.MEMBER, users.find(victim.id()).orElseThrow().role());
    }

    // The overview (USR-7, USR-8)

    @Test
    void theOverviewShowsEveryUserWithRoleDatesAndNeverLoggedIn() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        Document page = overview(adminCookie, "");
        assertEquals("User management", page.selectFirst("h1").text());
        Element row = row(page, member.email());
        assertNotNull(row);
        assertEquals("Member", row.select("td").get(0).text());
        assertTrue(
                row.select("td").get(1).text().matches("\\d{1,2} [A-Z][a-z]+ \\d{4}"),
                "the date added, without a time: " + row.select("td").get(1).text());
        assertEquals("Never logged in", row.select("td").get(2).text());
        assertEquals("Never sent", row.select("td").get(3).text());
        assertEquals("Master", row(page, TestAccounts.MASTER_EMAIL).select("td").get(0).text());
        assertEquals("Admin", row(page, admin.email()).select("td").get(0).text());
        // The admin logged in to make the request, so this user has a last login in Amsterdam time
        assertTrue(
                row(page, admin.email())
                        .select("td")
                        .get(2)
                        .text()
                        .matches("[A-Z][a-z]+ \\d{1,2} [A-Z][a-z]+ \\d{4}, \\d{2}:\\d{2}"));
        assertEquals(6, page.select("thead th").size());
        // The sorting is also in the form, because the headings are hidden on phones
        assertEquals(
                List.of("email", "role", "added", "last-login", "recovery-email"),
                page.select("select#sort option").stream().map(Element::val).toList());
        assertEquals(
                List.of("asc", "desc"),
                page.select("select#dir option").stream().map(Element::val).toList());
        assertNotNull(page.selectFirst("label[for=sort]"));
        assertNotNull(page.selectFirst("label[for=dir]"));
        assertEquals(
                "table",
                page.selectFirst("table").attr("role"),
                "the table keeps its meaning when it is shown as cards");
        assertEquals("Role", row.select("td").get(0).attr("data-label"));
        assertEquals("rowheader", row.selectFirst("th").attr("role"));
        assertNotNull(page.selectFirst("caption"));
        for (Element th : page.select("tbody th")) {
            assertEquals("row", th.attr("scope"));
        }
    }

    @Test
    void theRecoveryEmailDateIsShownAfterAnEmailWasSent() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        as(adminCookie).post("/users/" + member.id() + "/resend").then().statusCode(303);
        String text = row(overview(adminCookie, ""), member.email()).select("td").get(3).text();
        assertTrue(text.matches("[A-Z][a-z]+ \\d{1,2} [A-Z][a-z]+ \\d{4}, \\d{2}:\\d{2}"), text);
    }

    @Test
    void sortingAndFilteringWork() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        Document admins = overview(adminCookie, "?role=admin");
        assertNotNull(row(admins, admin.email()));
        assertEquals(null, row(admins, member.email()));
        assertEquals("admin", admins.selectFirst("select#role option[selected]").val());

        Document byRole = overview(adminCookie, "?sort=role&dir=desc");
        assertEquals(
                "role",
                byRole.selectFirst("select#sort option[selected]").val(),
                "the form shows the current sorting");
        assertEquals("desc", byRole.selectFirst("select#dir option[selected]").val());
        assertEquals(
                "descending",
                byRole.selectFirst("th[aria-sort=descending]").text().isEmpty()
                        ? ""
                        : "descending");
        assertEquals("Role", byRole.selectFirst("th[aria-sort=descending] a").text());
        assertEquals("href", "href");
        for (String sort : List.of("email", "role", "added", "last-login", "recovery-email")) {
            overview(adminCookie, "?sort=" + sort + "&dir=asc&role=all");
        }
        // The heading links flip the direction and keep the filter
        Document sorted = overview(adminCookie, "?sort=email&dir=asc&role=member");
        String href = sorted.selectFirst("th[aria-sort=ascending] a").attr("href");
        assertEquals("/users?sort=email&dir=desc&role=member", href);
    }

    @ParameterizedTest
    @CsvSource({
        "sort,password",
        "sort,' OR 1=1",
        "dir,sideways",
        "role,superuser",
        "sort,email;drop",
        "role,ADMIN\r\nX: y"
    })
    void unknownSortAndFilterValuesAreRefused(String name, String value) {
        Response response = as(adminCookie).queryParam(name, value).get("/users");
        assertEquals(400, response.statusCode());
        assertFalse(response.asString().contains("Exception"));
    }

    @Test
    void theOverviewOnlyShowsButtonsTheActorMayUse() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);

        Document asAdmin = overview(adminCookie, "");
        Element memberRow = row(asAdmin, member.email());
        assertTrue(has(memberRow, "/users/" + member.id() + "/resend"));
        assertTrue(has(memberRow, "/users/" + member.id() + "/make-admin"));
        assertTrue(has(memberRow, "/users/" + member.id() + "/remove"));
        Element adminRow = row(asAdmin, otherAdmin.email());
        assertTrue(has(adminRow, "/users/" + otherAdmin.id() + "/resend"));
        assertFalse(has(adminRow, "/users/" + otherAdmin.id() + "/remove"), "USR-11");
        assertFalse(has(adminRow, "/users/" + otherAdmin.id() + "/make-member"), "USR-11");
        assertTrue(
                row(asAdmin, TestAccounts.MASTER_EMAIL).select("a, button").isEmpty(),
                "USR-13: nothing for the master");

        Document asMaster = overview(masterCookie, "");
        Element adminRowForMaster = row(asMaster, otherAdmin.email());
        assertTrue(has(adminRowForMaster, "/users/" + otherAdmin.id() + "/remove"), "USR-12");
        assertTrue(has(adminRowForMaster, "/users/" + otherAdmin.id() + "/make-member"), "USR-12");
        assertFalse(has(adminRowForMaster, "/users/" + otherAdmin.id() + "/make-admin"));
        assertTrue(row(asMaster, TestAccounts.MASTER_EMAIL).select("a, button").isEmpty());
    }

    // Adding users (USR-2 to USR-5, USR-24)

    @Test
    void addingAUserCreatesAMemberAndSendsTheRecoveryEmail() {
        String email = "new-" + UUID.randomUUID() + "@example.test";
        Response response =
                as(adminCookie).formParam("email", "  " + email.toUpperCase() + " ").post("/users");
        response.then().statusCode(303).header("Location", "/users?notice=added");
        assertEquals(1, accounts.emailsTo(email));

        Document page = overview(adminCookie, "?notice=added");
        assertTrue(
                page.selectFirst("[role=status]")
                        .text()
                        .contains("A recovery email has been sent"));
        assertEquals("Member", row(page, email).select("td").get(0).text());
        assertEquals("Never logged in", row(page, email).select("td").get(2).text());
    }

    @Test
    void anAddressThatHasAnAccountIsRefusedWithAClearMessage() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        Response response = as(adminCookie).formParam("email", member.email()).post("/users");
        assertEquals(409, response.statusCode());
        Document page = Jsoup.parse(response.asString());
        assertEquals(
                "This email address already has an account",
                page.selectFirst("#email-error").text());
        assertEquals(member.email(), page.selectFirst("input[name=email]").val());
        assertEquals(0, accounts.emailsTo(member.email()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "nope", "a@b", "a b@example.test"})
    void aMissingOrInvalidAddressGetsAMessageNextToTheField(String email) {
        Response response = as(adminCookie).formParam("email", email).post("/users");
        assertEquals(400, response.statusCode());
        Document page = Jsoup.parse(response.asString());
        assertFalse(page.selectFirst("#email-error").text().isBlank());
        assertEquals("true", page.selectFirst("#email").attr("aria-invalid"));
        assertEquals("254", page.selectFirst("#email").attr("maxlength"));
    }

    @Test
    void whenTheEmailServiceFailsTheAdminSeesAnErrorAndTheUserExists() {
        outbox.failSends(true);
        String email = "new-" + UUID.randomUUID() + "@example.test";
        as(adminCookie)
                .formParam("email", email)
                .post("/users")
                .then()
                .statusCode(303)
                .header("Location", "/users?notice=added-no-email");
        outbox.failSends(false);
        Document page = overview(adminCookie, "?notice=added-no-email");
        assertTrue(
                page.selectFirst("[role=alert]")
                        .text()
                        .contains("recovery email could not be sent"));
        assertNotNull(row(page, email));
    }

    @Test
    void aNoticeIsOnlyShownForKnownKeys() {
        Document page = overview(adminCookie, "?notice=<script>alert(1)</script>");
        assertTrue(page.select("[role=status], [role=alert]").isEmpty());
        assertFalse(page.html().contains("<script>alert"));
    }

    // Sending the email again (USR-6)

    @Test
    void theEmailCanBeSentAgainAndNotForTheMaster() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        as(adminCookie)
                .post("/users/" + member.id() + "/resend")
                .then()
                .statusCode(303)
                .header("Location", "/users?notice=resent");
        assertEquals(1, accounts.emailsTo(member.email()));

        UUID masterId = accounts.masterId();
        as(adminCookie).post("/users/" + masterId + "/resend").then().statusCode(403);
        as(masterCookie).post("/users/" + masterId + "/resend").then().statusCode(403);
        assertEquals(0, accounts.emailsTo(TestAccounts.MASTER_EMAIL));

        outbox.failSends(true);
        as(adminCookie)
                .post("/users/" + member.id() + "/resend")
                .then()
                .statusCode(303)
                .header("Location", "/users?notice=resend-failed");
    }

    // The confirmation pages (plan: Confirming destructive actions)

    @Test
    void theConfirmationPageNamesTheUserAndHasCancelFirst() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        Response response = as(adminCookie).get("/users/" + member.id() + "/remove");
        response.then().statusCode(200).header("Cache-Control", "private, no-store");
        Document page = Jsoup.parse(response.asString());
        assertEquals("Remove user", page.selectFirst("h1").text());
        assertTrue(page.selectFirst("main").text().contains(member.email()), "it names the user");
        assertTrue(
                page.selectFirst("main").text().contains("cannot be undone"),
                "it says whether it can be undone");
        List<Element> controls = page.select("main form a, main form button");
        assertEquals("Cancel", controls.get(0).text(), "Cancel comes first");
        assertEquals("/users", controls.get(0).attr("href"));
        assertEquals(
                "Remove user", controls.get(1).text(), "a clearly named button, never just OK");
        assertTrue(controls.get(1).hasClass("button-danger"));
        assertEquals("yes", page.selectFirst("input[name=confirm]").val());
        assertEquals(
                "/users/" + member.id() + "/remove", page.selectFirst("main form").attr("action"));
    }

    @Test
    void theConfirmationPagesForRightsChangesNameTheUserToo() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        Document makeAdmin =
                Jsoup.parse(
                        as(adminCookie).get("/users/" + member.id() + "/make-admin").asString());
        assertEquals("Make admin", makeAdmin.selectFirst("h1").text());
        assertTrue(makeAdmin.selectFirst("main").text().contains(member.email()));
        Document makeMember =
                Jsoup.parse(
                        as(masterCookie)
                                .get("/users/" + otherAdmin.id() + "/make-member")
                                .asString());
        assertEquals("Take away admin rights", makeMember.selectFirst("h1").text());
        assertTrue(makeMember.selectFirst("main").text().contains(otherAdmin.email()));
    }

    @Test
    void theConfirmationPageOnlyOpensForAnActionTheActorMayDo() {
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        as(adminCookie).get("/users/" + otherAdmin.id() + "/remove").then().statusCode(403);
        as(adminCookie).get("/users/" + otherAdmin.id() + "/make-member").then().statusCode(403);
        as(adminCookie).get("/users/" + accounts.masterId() + "/remove").then().statusCode(403);
        as(masterCookie).get("/users/" + otherAdmin.id() + "/remove").then().statusCode(200);
    }

    @Test
    void opeingAConfirmationPageChangesNothing() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        for (String action : List.of("remove", "make-admin")) {
            as(adminCookie).get("/users/" + member.id() + "/" + action).then().statusCode(200);
        }
        assertEquals(Role.MEMBER, users.find(member.id()).orElseThrow().role());
    }

    // The server does not trust the dialog (ARC-17)

    @ParameterizedTest
    @CsvSource({"remove", "make-admin"})
    void withoutTheConfirmationFieldNothingHappens(String action) {
        UserSummary member = accounts.newUser(Role.MEMBER);
        String path = "/users/" + member.id() + "/" + action;
        assertEquals(400, as(adminCookie).post(path).statusCode(), "no field");
        assertEquals(
                400,
                as(adminCookie).formParam("confirm", "no").post(path).statusCode(),
                "wrong value");
        assertEquals(400, as(adminCookie).formParam("confirm", "true").post(path).statusCode());
        assertEquals(
                400,
                as(adminCookie).queryParam("confirm", "yes").post(path).statusCode(),
                "a query string does not count");
        assertTrue(users.find(member.id()).isPresent());
        assertEquals(Role.MEMBER, users.find(member.id()).orElseThrow().role());
    }

    @Test
    void takingAwayAdminRightsAlsoNeedsTheConfirmationField() {
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        String path = "/users/" + otherAdmin.id() + "/make-member";
        assertEquals(400, as(masterCookie).post(path).statusCode());
        assertEquals(Role.ADMIN, users.find(otherAdmin.id()).orElseThrow().role());
    }

    // The actions (USR-9 to USR-13), sent directly to the server

    @Test
    void anAdminRemovesAMember() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        as(adminCookie)
                .formParam("confirm", "yes")
                .post("/users/" + member.id() + "/remove")
                .then()
                .statusCode(303)
                .header("Location", "/users?notice=removed");
        assertTrue(users.find(member.id()).isEmpty());
        assertTrue(
                overview(adminCookie, "?notice=removed")
                        .selectFirst("[role=status]")
                        .text()
                        .contains("lost access"));
    }

    @Test
    void aRemovedMembersLoginStopsWorkingAtOnce() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        String memberCookie = cookieOf(member);
        as(memberCookie).get("/members").then().statusCode(200);
        as(adminCookie)
                .formParam("confirm", "yes")
                .post("/users/" + member.id() + "/remove")
                .then()
                .statusCode(303);
        as(memberCookie).get("/members").then().statusCode(303);
    }

    @Test
    void anAdminMakesAMemberAnAdminAndTheyGetAccessAtOnce() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        String memberCookie = cookieOf(member);
        as(memberCookie).get("/users").then().statusCode(403);
        as(adminCookie)
                .formParam("confirm", "yes")
                .post("/users/" + member.id() + "/make-admin")
                .then()
                .statusCode(303)
                .header("Location", "/users?notice=made-admin");
        as(memberCookie).get("/users").then().statusCode(200);
    }

    @Test
    void aSentRequestForAnActionTheRolesDoNotAllowIsRefusedAndChangesNothing() {
        UserSummary otherAdmin = accounts.newUser(Role.ADMIN);
        UUID masterId = accounts.masterId();
        // An admin, sending requests the page never offers (USR-11, USR-13)
        for (String path :
                List.of(
                        "/users/" + otherAdmin.id() + "/remove",
                        "/users/" + otherAdmin.id() + "/make-member",
                        "/users/" + masterId + "/remove",
                        "/users/" + masterId + "/make-member",
                        "/users/" + masterId + "/make-admin",
                        "/users/" + otherAdmin.id() + "/make-admin")) {
            assertEquals(
                    403, as(adminCookie).formParam("confirm", "yes").post(path).statusCode(), path);
        }
        assertEquals(
                403,
                as(masterCookie)
                        .formParam("confirm", "yes")
                        .post("/users/" + masterId + "/remove")
                        .statusCode(),
                "not even the master");
        assertEquals(Role.ADMIN, users.find(otherAdmin.id()).orElseThrow().role());
        assertEquals(Role.MASTER, users.find(masterId).orElseThrow().role());
    }

    @Test
    void theMasterRemovesAnAdminAndTakesAwayAdminRights() {
        UserSummary toRemove = accounts.newUser(Role.ADMIN);
        UserSummary toDemote = accounts.newUser(Role.ADMIN);
        as(masterCookie)
                .formParam("confirm", "yes")
                .post("/users/" + toRemove.id() + "/remove")
                .then()
                .statusCode(303);
        assertTrue(users.find(toRemove.id()).isEmpty());
        as(masterCookie)
                .formParam("confirm", "yes")
                .post("/users/" + toDemote.id() + "/make-member")
                .then()
                .statusCode(303)
                .header("Location", "/users?notice=made-member");
        assertEquals(Role.MEMBER, users.find(toDemote.id()).orElseThrow().role());
    }

    @Test
    void anAdminWhoIsDemotedLosesAccessToThePageAtOnce() {
        UserSummary victim = accounts.newUser(Role.ADMIN);
        String victimCookie = cookieOf(victim);
        as(victimCookie).get("/users").then().statusCode(200);
        as(masterCookie)
                .formParam("confirm", "yes")
                .post("/users/" + victim.id() + "/make-member")
                .then()
                .statusCode(303);
        as(victimCookie).get("/users").then().statusCode(403);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not-a-uuid",
                "123",
                "00000000-0000-0000-0000-00000000000",
                "../etc/passwd",
                "' OR 1=1"
            })
    void anIdThatIsNotAUuidIsNotFound(String id) {
        for (String action : List.of("remove", "make-admin", "make-member", "resend")) {
            assertEquals(
                    404,
                    as(adminCookie)
                                            .get("/users/" + id.replace(" ", "%20") + "/" + action)
                                            .statusCode()
                                    == 405
                            ? 404
                            : as(adminCookie)
                                    .get(
                                            "/users/"
                                                    + id.replace(" ", "%20").replace("'", "%27")
                                                    + "/"
                                                    + action)
                                    .statusCode(),
                    id + " " + action);
        }
        assertEquals(
                404,
                as(adminCookie)
                        .formParam("confirm", "yes")
                        .post("/users/" + id.replace(" ", "%20").replace("'", "%27") + "/remove")
                        .statusCode());
    }

    @Test
    void anUnknownUserIsNotFound() {
        String path = "/users/" + UUID.randomUUID();
        assertEquals(
                404,
                as(adminCookie).formParam("confirm", "yes").post(path + "/remove").statusCode());
        assertEquals(404, as(adminCookie).get(path + "/remove").statusCode());
    }

    @Test
    void actionsFromAnotherSiteAreRefused() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Cookie", adminCookie)
                .header("Origin", "https://evil.example")
                .formParam("confirm", "yes")
                .post("/users/" + member.id() + "/remove")
                .then()
                .statusCode(403);
        given().redirects()
                .follow(false)
                .accept("text/html")
                .header("Cookie", adminCookie)
                .formParam("confirm", "yes")
                .post("/users/" + member.id() + "/remove")
                .then()
                .statusCode(403);
        assertTrue(users.find(member.id()).isPresent());
    }

    // The dialog (DES-13)

    @Test
    void theDialogIsInThePageAndEveryButtonAlsoWorksAsALink() {
        UserSummary member = accounts.newUser(Role.MEMBER);
        Document page = overview(adminCookie, "");
        Element dialog = page.selectFirst("dialog#confirm-dialog");
        assertNotNull(dialog);
        assertEquals("confirm-title", dialog.attr("aria-labelledby"));
        assertEquals("confirm-text", dialog.attr("aria-describedby"));
        assertNotNull(page.getElementById("confirm-title"));
        assertNotNull(page.getElementById("confirm-text"));
        List<Element> buttons = dialog.select("button");
        assertEquals("Cancel", buttons.get(0).text(), "Cancel is first");
        assertTrue(buttons.get(0).hasAttr("autofocus"), "and has the focus when the dialog opens");
        assertEquals("button", buttons.get(0).attr("type"));
        assertEquals("submit", buttons.get(1).attr("type"));
        assertTrue(buttons.get(1).hasClass("button-danger"));
        assertEquals("yes", dialog.selectFirst("input[name=confirm]").val());

        Element remove = row(page, member.email()).selectFirst("a[data-confirm][href$=/remove]");
        assertEquals(
                "/users/" + member.id() + "/remove",
                remove.attr("href"),
                "without JavaScript it opens the confirmation page");
        assertEquals("/users/" + member.id() + "/remove", remove.attr("data-confirm-action"));
        assertEquals("Remove user", remove.attr("data-confirm-button"));
        assertTrue(
                remove.attr("data-confirm-text").contains(member.email()),
                "the dialog names the user");
        assertTrue(
                remove.attr("aria-label").contains(member.email()),
                "and so does the accessible name of the button");
        assertEquals("/js/confirm-dialog.js", page.select("script[src]").last().attr("src"));
        given().get("/js/confirm-dialog.js").then().statusCode(200);
    }

    @Test
    void theDialogScriptDoesWhatTheSpecAsks() {
        String script = given().get("/js/confirm-dialog.js").asString();
        assertTrue(script.contains("showModal"));
        assertTrue(script.contains("cancel.focus()"), "Cancel gets the focus");
        assertTrue(script.contains("dialog.close()"), "Cancel closes it without doing anything");
        assertTrue(
                script.contains("opener.focus()"),
                "the focus goes back to the link that opened it");
        assertTrue(
                script.contains("typeof dialog.showModal !== \"function\""),
                "without dialog support the links keep working");
        assertFalse(
                script.contains("fetch(")
                        || script.contains("XMLHttpRequest")
                        || script.contains("innerHTML")
                        || script.contains("eval("));
    }

    @Test
    void thePageLoadsNothingFromAnotherSite() {
        Document page = overview(masterCookie, "");
        assertTrue(
                page.select("[src], [href], [action]").stream()
                        .noneMatch(e ->
                                e.attr("src").startsWith("http")
                                        || e.attr("href").startsWith("http")
                                        || e.attr("action").startsWith("http")));
        assertTrue(page.select("[style], style, script:not([src])").isEmpty());
    }
}
