package uk.gov.hmcts.reform.refunds.smoke;

import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;


@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SmokeTest {
    @Value("${test.url}")
    private String testUrl;

    @BeforeAll
    public void setUp() {
        RestAssured.baseURI = testUrl;
    }

    @Test
    public void healthCheck() {
        given()
            .relaxedHTTPSValidation()
            .header(CONTENT_TYPE, "application/json")
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body(
                "status", equalTo("UP"));
        assertFalse(testUrl.isEmpty(),"Sample Test for the template....");
    }
}
