package org.example.website;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PageResourceTest {

    @Test
    void homeShowsTheOrchestraName() {
        given().when().get("/").then().statusCode(200).body(containsString("PLACEHOLDER_NAME"));
    }

    @Test
    void healthCheckIsUp() {
        given().when().get("/q/health/ready").then().statusCode(200).body(containsString("UP"));
    }
}
