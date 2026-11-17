package org.example.website.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** HOS-32 and ARC-8: friendly error pages and one error format, without technical details. */
@QuarkusTest
class ErrorHandlingTest {

    @ParameterizedTest
    @CsvSource({
        "/test-only/errors/denied,403,Access denied",
        "/test-only/errors/missing,404,Page not found",
        "/test-only/errors/invalid,400,Check your input",
        "/test-only/errors/conflict,409,Not possible right now",
        "/test-only/errors/unavailable,503,Temporarily unavailable",
        "/test-only/errors/crash,500,Something went wrong",
        "/does-not-exist,404,Page not found"
    })
    void browsersGetAFriendlyPageWithTheRequestId(String path, int status, String title) {
        Response response = given().accept("text/html").get(path);
        response.then()
                .statusCode(status)
                .contentType(startsWith("text/html"))
                .header("Cache-Control", "no-store")
                .body(containsString("<h1>" + title + "</h1>"))
                .body(containsString("lang=\"en\""));
        String requestId = response.header("X-Request-ID");
        assertTrue(response.asString().contains(requestId), "the page shows the request ID");
    }

    @ParameterizedTest
    @CsvSource({
        "/test-only/errors/denied",
        "/test-only/errors/missing",
        "/test-only/errors/invalid",
        "/test-only/errors/conflict",
        "/test-only/errors/unavailable",
        "/test-only/errors/crash"
    })
    void noTechnicalDetailsAreShown(String path) {
        given().accept("text/html")
                .get(path)
                .then()
                .body(not(containsString(ErrorProbeResource.SECRET)))
                .body(not(containsString("Exception")))
                .body(not(containsString("at org.")))
                .body(not(containsString("quarkus")));
        given().accept("application/json")
                .get(path)
                .then()
                .body(not(containsString(ErrorProbeResource.SECRET)))
                .body(not(containsString("Exception")));
    }

    @Test
    void jsonClientsGetAProblemDocument() {
        Response response = given().accept("application/json").get("/test-only/errors/denied");
        response.then()
                .statusCode(403)
                .contentType("application/problem+json")
                .header("Cache-Control", "no-store")
                .body("status", org.hamcrest.Matchers.equalTo(403))
                .body("title", org.hamcrest.Matchers.equalTo("Access denied"))
                .body("type", org.hamcrest.Matchers.equalTo("about:blank"));
        assertEquals(response.header("X-Request-ID"), response.jsonPath().getString("requestId"));
    }

    @Test
    void aBrowserThatAcceptsBothGetsThePage() {
        given().accept("text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .get("/test-only/errors/denied")
                .then()
                .contentType(startsWith("text/html"));
    }

    @Test
    void aWrongMethodGivesAFriendlyPage() {
        given().accept("text/html")
                .get("/test-only/errors/post-only")
                .then()
                .statusCode(405)
                .body(containsString("<h1>Request not possible</h1>"));
    }

    @Test
    void errorPagesKeepTheSecurityHeaders() {
        given().get("/test-only/errors/crash")
                .then()
                .header("Content-Security-Policy", containsString("default-src 'self'"))
                .header("X-Content-Type-Options", "nosniff");
    }

    @Test
    void unexpectedErrorsAreLoggedWithTheirStackTrace() {
        List<LogRecord> records = new ArrayList<>();
        Handler handler =
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        records.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                };
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        root.addHandler(handler);
        try {
            given().get("/test-only/errors/crash").then().statusCode(500);
        } finally {
            root.removeHandler(handler);
        }
        assertTrue(
                records.stream()
                        .anyMatch(r ->
                                r.getLevel().intValue() >= Level.SEVERE.intValue()
                                        && r.getThrown() instanceof IllegalStateException
                                        && ErrorProbeResource.SECRET.equals(
                                                r.getThrown().getMessage())),
                "the error is logged with its exception");
    }

    @Test
    void theVersionIsOnlyShownOnThe500Page() {
        given().accept("text/html")
                .get("/test-only/errors/crash")
                .then()
                .body(containsString("version <code>dev</code>"));
        given().accept("text/html")
                .get("/test-only/errors/denied")
                .then()
                .body(not(containsString("version")));
        given().accept("text/html")
                .get("/does-not-exist")
                .then()
                .body(not(containsString("version")));
    }
}
