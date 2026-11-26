package org.example.website.platform;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** HRD-6: security headers on all responses. */
@QuarkusTest
class SecurityHeadersTest {

    @ParameterizedTest
    @CsvSource({"/,200", "/does-not-exist,404", "/q/health/ready,200"})
    void everyResponseHasTheSecurityHeaders(String path, int status) {
        ValidatableResponse response = given().get(path).then().statusCode(status);
        response.header("Content-Security-Policy", containsString("default-src 'self'"))
                .header("Content-Security-Policy", containsString("script-src 'self'"))
                .header("Content-Security-Policy", containsString("object-src 'none'"))
                .header("Content-Security-Policy", containsString("frame-ancestors 'none'"))
                .header("Content-Security-Policy", containsString("form-action 'self'"))
                .header("Content-Security-Policy", containsString("base-uri 'self'"))
                .header("Strict-Transport-Security", containsString("max-age="))
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "strict-origin-when-cross-origin")
                .header("Permissions-Policy", containsString("camera=()"))
                .header("Permissions-Policy", containsString("microphone=()"))
                .header("Permissions-Policy", containsString("geolocation=()"))
                .header("Cross-Origin-Opener-Policy", "same-origin")
                .header("Cross-Origin-Resource-Policy", "same-origin");
    }

    @ParameterizedTest
    @CsvSource({"/"})
    void contentSecurityPolicyAllowsNoInlineScriptsOrStyles(String path) {
        String policy = given().get(path).header("Content-Security-Policy");
        org.junit.jupiter.api.Assertions.assertFalse(policy.contains("unsafe-inline"));
        org.junit.jupiter.api.Assertions.assertFalse(policy.contains("unsafe-eval"));
    }

    @Test
    void theImagesBucketIsTheOnlyOtherPlaceImagesAndUploadsMayGo() {
        String policy = given().get("/").header("Content-Security-Policy");
        org.junit.jupiter.api.Assertions.assertTrue(
                policy.contains("img-src 'self' https://images.example.test/;"), policy);
        org.junit.jupiter.api.Assertions.assertTrue(
                policy.contains("connect-src 'self' https://images.example.test/ https://files"),
                policy);
        org.junit.jupiter.api.Assertions.assertTrue(
                policy.contains(
                        "connect-src 'self' https://images.example.test/ https://files.example.test/;"),
                policy);
        org.junit.jupiter.api.Assertions.assertTrue(
                policy.contains("media-src 'self' https://files.example.test/;"), policy);
        org.junit.jupiter.api.Assertions.assertFalse(
                policy.contains(
                        "img-src 'self' https://images.example.test/ https://files.example.test"),
                "pictures never come from the private bucket");
        // Pictures from blob: or data: addresses are not allowed. The upload script reads the
        // chosen
        // file with createImageBitmap for that reason (see image-upload.js).
        org.junit.jupiter.api.Assertions.assertFalse(policy.contains("blob:"));
        org.junit.jupiter.api.Assertions.assertFalse(policy.contains("data:"));
        org.junit.jupiter.api.Assertions.assertTrue(policy.contains("script-src 'self';"));
    }
}
