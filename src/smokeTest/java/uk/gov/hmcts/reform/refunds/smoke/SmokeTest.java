package uk.gov.hmcts.reform.refunds.smoke;

import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.RefundApplication;
import uk.gov.hmcts.reform.refunds.smoke.config.TestConfigProperties;
import uk.gov.hmcts.reform.refunds.smoke.idam.IdamService;
import uk.gov.hmcts.reform.refunds.smoke.s2s.S2sTokenService;

import static io.restassured.RestAssured.expect;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static uk.gov.hmcts.reform.refunds.smoke.idam.IdamService.CMC_CASE_WORKER_GROUP;


@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("smoke")
@SpringBootTest(classes = {RefundApplication.class})
public class SmokeTest {

    @Autowired
    private TestConfigProperties testProps;

    @Value("${test.url}")
    private String testUrl;

    @Autowired
    private IdamService idamService;
    @Autowired
    private S2sTokenService s2sTokenService;

    private static String USER_TOKEN;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED;

    @BeforeAll
    public void setUp() throws Exception {
        RestAssured.baseURI = testUrl;
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN =  idamService.createUserWith(CMC_CASE_WORKER_GROUP, "payments-refund")
                .getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
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
    void testGetReasons() {
        expect().given()
            .relaxedHTTPSValidation()
            .header("Authorization", USER_TOKEN)
            .header("ServiceAuthorization", SERVICE_TOKEN)
            .contentType(APPLICATION_JSON_VALUE)
            .accept(APPLICATION_JSON_VALUE)
            .when()
            .get("/refund/reasons")
            .then()
            .statusCode(200);
        assertFalse(testUrl.isEmpty(), "The test has completed...");
    }
}
