package uk.gov.hmcts.reform.refunds.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;
import uk.gov.hmcts.reform.refunds.RestActions;
import uk.gov.hmcts.reform.refunds.config.TestContextConfiguration;
import uk.gov.hmcts.reform.refunds.config.security.filiters.ServiceAndUserAuthFilter;
import uk.gov.hmcts.reform.refunds.config.security.utils.SecurityUtils;
import uk.gov.hmcts.reform.refunds.dto.RefundStatus;
import uk.gov.hmcts.reform.refunds.dto.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.reform.refunds.filters.ServiceAndUserAuthFilterTest.getUserInfoBasedOnUidRoles;


@RunWith(SpringRunner.class)
@SpringBootTest
@EnableFeignClients
@AutoConfigureMockMvc()
@ContextConfiguration(classes = TestContextConfiguration.class)
@ActiveProfiles({"local", "test"})
//@TestPropertySource(locations="classpath:application-local.yaml")
public class RefundStatusUpdateControllerTest {

    MockMvc mvc;

    RefundStatusUpdateRequest refundStatusUpdateRequest;

    @Autowired
    RefundsRepository refundsRepository;
    @Autowired
    ServiceAuthFilter serviceAuthFilter;
    @InjectMocks
    ServiceAndUserAuthFilter serviceAndUserAuthFilter;
    @MockBean
    SecurityUtils securityUtils;
    RestActions restActions;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    private JwtDecoder jwtDecoder;
    @Autowired
    private ObjectMapper objectMapper;

    public static RefundStatusUpdateRequest createRefundStatusUpdateRequest() {
        return RefundStatusUpdateRequest.RefundRequestWith().status(RefundStatus.REJECTED)
            .reason("Abc")
            .build();
    }

    public static RefundRequest createRefundRequest() {
        return RefundRequest.refundRequestWith().paymentReference("RC-1626-4388-8013-9800")
            .refundAmount(BigDecimal.valueOf(100.00))
            .refundReason("abc").
                build();
    }

    @Before
    public void setUp() {
        //OIDC UserInfo Mocking
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUidRoles("UID123", "payments"));

        MockMvc mvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        this.restActions = new RestActions(mvc, objectMapper);

        restActions
            .withAuthorizedService("cmc")
            .withAuthorizedUser()
            .withReturnUrl("https://www.gooooogle.com");
    }

    @Test
    @WithMockUser(authorities = "payments")
    public void testRefundStatusUpdateRequest() throws Exception {

        RefundRequest refundRequest = createRefundRequest();

        RefundStatusUpdateRequest refundStatusUpdateRequest = createRefundStatusUpdateRequest();

        //Post request
        ResultActions resultActions = restActions.post("/refund", refundRequest);

        Assert.assertNotNull(resultActions.andReturn().getResponse().getContentAsString());

        //PATCH Request
        ResultActions patchRequest = restActions.patch("/refund/RC-1626-4388-8013-9800", refundStatusUpdateRequest);

        Assert.assertNotNull(patchRequest.andReturn().getResponse().getContentAsString());

    }

}
