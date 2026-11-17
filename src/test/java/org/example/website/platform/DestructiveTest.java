package org.example.website.platform;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * ARC-17: a destructive request is a POST with an explicit confirmation field, or it is refused.
 */
@QuarkusTest
class DestructiveTest {

    private static final String PATH = "/test-only/destructive/delete";

    private static int deletions() {
        return DestructiveProbeResource.DELETIONS.get();
    }

    @Test
    void aConfirmedPostPerformsTheAction() {
        int before = deletions();
        given().formParam("confirm", "yes").post(PATH).then().statusCode(200);
        assertEquals(before + 1, deletions());
    }

    @Test
    void aPostWithoutTheConfirmationFieldIsRefusedAndNothingHappens() {
        int before = deletions();
        given().accept("text/html").post(PATH).then().statusCode(400);
        given().accept("text/html").formParam("other", "yes").post(PATH).then().statusCode(400);
        assertEquals(before, deletions());
    }

    @Test
    void anyOtherConfirmationValueIsRefused() {
        int before = deletions();
        for (String value : new String[] {"", "no", "true", "YES", "yes ", "1", "on"}) {
            given().formParam("confirm", value).post(PATH).then().statusCode(400);
        }
        assertEquals(before, deletions());
    }

    @Test
    void aGetRequestIsRefusedAndNothingHappens() {
        int before = deletions();
        given().get(PATH).then().statusCode(405);
        given().queryParam("confirm", "yes").get(PATH).then().statusCode(405);
        // Even a destructive endpoint wrongly declared as GET is refused by the guard.
        given().queryParam("confirm", "yes")
                .get("/test-only/destructive/delete-by-get")
                .then()
                .statusCode(405);
        assertEquals(before, deletions());
    }

    @Test
    void aConfirmationInTheQueryStringOfAPostDoesNotCount() {
        int before = deletions();
        given().queryParam("confirm", "yes").post(PATH).then().statusCode(400);
        assertEquals(before, deletions());
    }
}
