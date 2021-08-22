package uk.gov.hmcts.reform.refunds.smoke;

import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.RefundApplication;

import static io.restassured.RestAssured.expect;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;


@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("smoke")
@SpringBootTest(classes = {RefundApplication.class})
public class SmokeTest {
    @Value("${test.url}")
    private String testUrl;

    @BeforeAll
    void setUp() {
        RestAssured.baseURI = testUrl;
    }

    @Test
    void healthCheck() {
        log.info("TEST - healthCheck() started");
        given()
            .relaxedHTTPSValidation()
            .header(CONTENT_TYPE, "application/json")
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body(
                "status", equalTo("UP"));
        assertFalse(testUrl.isEmpty(), "Sample Test for the template....");
        log.info("TEST - healthCheck() finished");
    }

    @Test
    void getReasons() {
        expect().given()
            .relaxedHTTPSValidation()
            .header("Authorization", "user")
            .header("ServiceAuthorization", "Services")
            .contentType(APPLICATION_JSON_VALUE)
            .accept(APPLICATION_JSON_VALUE)
            .when()
            .get("/refund/reasons")
            .then()
            .statusCode(200);
        assertTrue(true, "The Reasons for the Refunds...");
    }
}
