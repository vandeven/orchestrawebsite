package org.example.website.site;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** DES-1, DES-5: the stylesheets, the script and the logo are served by the website itself. */
@QuarkusTest
class StaticFilesTest {

    @Test
    void stylesheetsAndScriptAreServed() {
        given().get("/css/tokens.css").then().statusCode(200).contentType(startsWith("text/css"));
        given().get("/css/base.css").then().statusCode(200).contentType(startsWith("text/css"));
        given().get("/js/menu.js").then().statusCode(200).contentType(containsString("javascript"));
    }

    @Test
    void staticFilesGetTheSecurityHeaders() {
        given().get("/css/base.css")
                .then()
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", containsString("default-src 'self'"));
    }

    @Test
    void theLogoIsAnSvgWithoutScriptsOrExternalReferences() {
        given().get("/favicon.svg")
                .then()
                .statusCode(200)
                .contentType(startsWith("image/svg+xml"))
                .body(not(containsString("<script")))
                .body(not(containsString("<foreignObject")))
                .body(not(containsString("xlink:href")))
                .body(not(containsString("href=")))
                .body(not(containsString("onload")))
                .body(not(containsString("url(")));
    }

    @Test
    void thePhoneMenuScriptTouchesNothingOutsideItsOwnElements() {
        given().get("/js/menu.js")
                .then()
                .body(not(containsString("eval(")))
                .body(not(containsString("innerHTML")))
                .body(not(containsString("document.write")))
                .body(not(containsString("XMLHttpRequest")))
                .body(not(containsString("fetch(")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"/", "/login", "/privacy", "/does-not-exist", "/forgot-password"})
    void everyStyleSheetScriptAndIconCarriesTheVersionOfTheBuild(String path) {
        String html = given().accept("text/html").get(path).asString();
        var assets =
                org.jsoup.Jsoup.parse(html)
                        .select("link[rel=stylesheet][href], link[rel=icon][href], script[src]");
        org.junit.jupiter.api.Assertions.assertFalse(assets.isEmpty());
        for (var asset : assets) {
            String address = asset.hasAttr("src") ? asset.attr("src") : asset.attr("href");
            org.junit.jupiter.api.Assertions.assertTrue(
                    address.matches("/[a-z/.-]+\\?v=[A-Za-z0-9._-]+"), path + ": " + address);
            // The address with the version still gives the file
            given().get(address).then().statusCode(200);
        }
    }
}
