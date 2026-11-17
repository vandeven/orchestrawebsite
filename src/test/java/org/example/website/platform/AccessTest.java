package org.example.website.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.example.website.platform.api.AccessLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** ARC-7: access is declared on every endpoint and enforced on the server, deny by default. */
@QuarkusTest
class AccessTest {

    private static RequestSpecification as(String level) {
        RequestSpecification spec = given().redirects().follow(false).accept("text/html");
        return level.equals("ANONYMOUS") ? spec : spec.header("X-Test-Level", level);
    }

    /** level of the visitor, page, expected status: every role against every access level. */
    @ParameterizedTest
    @CsvSource({
        "ANONYMOUS,public,200",
        "ANONYMOUS,member,303",
        "ANONYMOUS,admin,303",
        "ANONYMOUS,master,303",
        "MEMBER,public,200",
        "MEMBER,member,200",
        "MEMBER,admin,403",
        "MEMBER,master,403",
        "ADMIN,public,200",
        "ADMIN,member,200",
        "ADMIN,admin,200",
        "ADMIN,master,403",
        "MASTER,public,200",
        "MASTER,member,200",
        "MASTER,admin,200",
        "MASTER,master,200"
    })
    void everyRoleAgainstEveryLevel(String visitor, String page, int status) {
        as(visitor.equals("ANONYMOUS") ? "ANONYMOUS" : visitor)
                .get("/test-only/access/" + page)
                .then()
                .statusCode(status);
    }

    @Test
    void anAnonymousVisitorIsSentToTheLoginPageAndReturnedAfterwards() {
        as("ANONYMOUS")
                .get("/test-only/access/member?x=1&y=a b")
                .then()
                .statusCode(303)
                .header(
                        "Location",
                        "/login?return=%2Ftest-only%2Faccess%2Fmember%3Fx%3D1%26y%3Da%2520b")
                .header("Cache-Control", "no-store");
    }

    @Test
    void theLoginRedirectNeverLeavesTheWebsite() {
        // A hostile path or host cannot change where the visitor is sent.
        String location =
                as("ANONYMOUS")
                        .header("Host", "evil.example")
                        .get("/test-only/access/admin")
                        .then()
                        .statusCode(303)
                        .extract()
                        .header("Location");
        org.junit.jupiter.api.Assertions.assertTrue(location.startsWith("/login?return="));
        org.junit.jupiter.api.Assertions.assertFalse(location.contains("evil"));
    }

    @Test
    void aWrongLevelShowsTheAccessDeniedPageWithoutLeakingTheContent() {
        as("MEMBER")
                .get("/test-only/access/admin")
                .then()
                .statusCode(403)
                .body(containsString("Access denied"))
                .body(not(containsString("admin</")));
    }

    @Test
    void aStateChangingRequestFromAnAnonymousVisitorIsRefusedNotRedirected() {
        as("ANONYMOUS").post("/test-only/access/admin-post").then().statusCode(403);
        as("MEMBER").post("/test-only/access/admin-post").then().statusCode(403);
        as("ADMIN")
                .post("/test-only/access/admin-post")
                .then()
                .statusCode(200)
                .body(equalTo("done"));
    }

    @ParameterizedTest
    @CsvSource({"ANONYMOUS", "MEMBER", "ADMIN", "MASTER"})
    void anEndpointWithoutAnAnnotationIsRefusedForEveryone(String visitor) {
        as(visitor)
                .get("/test-only/access/unannotated")
                .then()
                .statusCode(403)
                .body(not(containsString("should never be shown")));
    }

    @Test
    void aClassLevelAnnotationAppliesToItsMethodsAndAMethodCanOverrideIt() {
        as("ANONYMOUS").get("/test-only/class-level/inherited").then().statusCode(303);
        as("MEMBER").get("/test-only/class-level/inherited").then().statusCode(200);
        as("MEMBER").get("/test-only/class-level/overridden").then().statusCode(403);
        as("ADMIN").get("/test-only/class-level/overridden").then().statusCode(200);
    }

    @Test
    void levelsAreOrdered() {
        org.junit.jupiter.api.Assertions.assertTrue(AccessLevel.MASTER.allows(AccessLevel.ADMIN));
        org.junit.jupiter.api.Assertions.assertTrue(AccessLevel.ADMIN.allows(AccessLevel.MEMBER));
        org.junit.jupiter.api.Assertions.assertTrue(AccessLevel.MEMBER.allows(AccessLevel.PUBLIC));
        org.junit.jupiter.api.Assertions.assertFalse(AccessLevel.MEMBER.allows(AccessLevel.ADMIN));
        org.junit.jupiter.api.Assertions.assertFalse(AccessLevel.ADMIN.allows(AccessLevel.MASTER));
        org.junit.jupiter.api.Assertions.assertFalse(AccessLevel.PUBLIC.allows(AccessLevel.MEMBER));
    }

    @Test
    void theHomePageIsPublic() {
        RestAssured.given().get("/").then().statusCode(200);
    }
}
