package org.example.website.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

/** LGG-3: every request gets a server-generated request ID. */
@QuarkusTest
class RequestIdTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    @Test
    void pageResponseHasARequestId() {
        given().get("/").then().header("X-Request-ID", matchesPattern(UUID_PATTERN));
    }

    @Test
    void errorResponseHasARequestId() {
        given().get("/does-not-exist")
                .then()
                .statusCode(404)
                .header("X-Request-ID", matchesPattern(UUID_PATTERN));
    }

    @Test
    void everyRequestGetsADifferentId() {
        String first = given().get("/").header("X-Request-ID");
        String second = given().get("/").header("X-Request-ID");
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }

    @Test
    void anIdSentByTheBrowserIsNotTrusted() {
        given().header("X-Request-ID", "attacker-chosen-value\r\nX: y")
                .get("/")
                .then()
                .header("X-Request-ID", not(equalTo("attacker-chosen-value")))
                .header("X-Request-ID", matchesPattern(UUID_PATTERN));
    }

    @Test
    void theLogContextHoldsTheSameIdAsTheResponseHeader() {
        Response response = given().get("/test-only/request-id");
        response.then().statusCode(200);
        org.junit.jupiter.api.Assertions.assertEquals(
                response.header("X-Request-ID"), response.asString());
    }
}
