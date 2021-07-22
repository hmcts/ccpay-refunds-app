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
import uk.gov.hmcts.reform.refunds.config.launchdarkly.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.RefundsService;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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

    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;

    private static String userToken;
    private static String serviceToken;
    private static boolean tokenInitialized;

    @Before
    public void setUp() {
        if (!tokenInitialized) {
            userToken = idamService.createUserWith(CMC_CITIZEN_GROUP, "citizen").getAuthorisationToken();
            serviceToken = s2sTokenService.getS2sToken(testProps.s2sServiceName, testProps.s2sServiceSecret);
            tokenInitialized = true;
        }
    }

    @Test
    public void testRefundsRequest() throws Exception {
        when(featureToggler.getBooleanValue(anyString(),anyBoolean())).thenReturn(false);
        Response response = RestAssured.given()
            .header("ServiceAuthorization", serviceToken)
            .contentType(ContentType.JSON)
            .when()
            .get("/refundstest");

        Assert.assertNotNull(response.andReturn().asString());
    }

    @Test
    public  void testRefundsPostRequest() throws Exception {
        Response response = RestAssured.given()
            .header("Authorization",userToken)
            .header("ServiceAuthorization", serviceToken)
            .contentType(ContentType.JSON)
            .when()
            .post("/refunds");
        Assert.assertNotNull(response.andReturn().asString());
    }
}
