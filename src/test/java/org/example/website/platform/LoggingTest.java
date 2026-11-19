package org.example.website.platform;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/** LGG-6 and LGG-7: the log level, and no logging of health check requests. */
@QuarkusTest
class LoggingTest {

    private static List<LogRecord> capture(Runnable action) {
        List<LogRecord> records = new CopyOnWriteArrayList<>();
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
            action.run();
        } finally {
            root.removeHandler(handler);
        }
        return records;
    }

    @Test
    void healthCheckRequestsWriteNoLogLines() {
        List<LogRecord> records =
                capture(() -> {
                    given().get("/q/health/ready").then().statusCode(200);
                    given().get("/q/health/live").then().statusCode(200);
                    given().get("/q/health").then().statusCode(200);
                });
        assertEquals(List.of(), records.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void theLogLevelFromTheConfigurationIsApplied() {
        // The test profile uses the default INFO: DEBUG is off, INFO is on.
        assertEquals(Level.INFO, java.util.logging.Logger.getLogger("").getLevel());
    }

    @Test
    void healthCheckAnswersWithoutVersionOrDetails() {
        Response response = given().get("/q/health/ready");
        String body = response.asString();
        assertTrue(body.contains("UP"));
        assertTrue(!body.contains("dev"), "the version is not in the health check (HOS-35)");
    }
}
