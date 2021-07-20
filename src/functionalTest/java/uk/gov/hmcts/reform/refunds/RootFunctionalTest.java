package uk.gov.hmcts.reform.refunds;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import net.serenitybdd.junit.spring.integration.SpringIntegrationSerenityRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.refunds.config.IdamService;
import uk.gov.hmcts.reform.refunds.config.S2sTokenService;
import uk.gov.hmcts.reform.refunds.config.TestConfigProperties;
import uk.gov.hmcts.reform.refunds.config.TestContextConfiguration;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.RefundsService;

import static uk.gov.hmcts.reform.refunds.config.IdamService.CMC_CITIZEN_GROUP;


@RunWith(SpringIntegrationSerenityRunner.class)
@SpringBootTest
@EnableFeignClients
@ContextConfiguration(classes = TestContextConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yaml")
public class RootFunctionalTest {

    @Autowired
    RefundsService refundsService;

    @Autowired
    RefundsRepository refundsRepository;

    @Autowired
    private TestConfigProperties testProps;

    @Autowired
    private IdamService idamService;

    @Autowired
    private S2sTokenService s2sTokenService;

    private static String USER_TOKEN;
    private static String SERVICE_TOKEN;
    private static boolean TOKENS_INITIALIZED;

    @Before
    public void setUp() {
        if (!TOKENS_INITIALIZED) {
            USER_TOKEN = idamService.createUserWith(CMC_CITIZEN_GROUP, "citizen").getAuthorisationToken();
            SERVICE_TOKEN = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            TOKENS_INITIALIZED = true;
        }
    }

    @Test
    public void testRefundsRequest() throws Exception {
        Response response = RestAssured.given()
            .header("ServiceAuthorization", SERVICE_TOKEN)
            .contentType(ContentType.JSON)
            .when()
            .get("/refundstest");

        Assert.assertNotNull(response.andReturn().asString());
    }

    @Test
    public  void testRefundsPostRequest() throws Exception {
        Response response = RestAssured.given()
            .header("Authorization",USER_TOKEN)
            .header("ServiceAuthorization", SERVICE_TOKEN)
            .contentType(ContentType.JSON)
            .when()
            .post("/refunds");
        Assert.assertNotNull(response.andReturn().asString());
    }
}
