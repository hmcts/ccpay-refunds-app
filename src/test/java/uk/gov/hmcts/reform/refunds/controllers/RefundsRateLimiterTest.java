package uk.gov.hmcts.reform.refunds.controllers;

import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.services.IacService;
import uk.gov.hmcts.reform.refunds.services.NotificationServiceImpl;
import uk.gov.hmcts.reform.refunds.services.RefundNotificationService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
    "resilience4j.ratelimiter.instances.refundsApi.limit-for-period=1",
    "resilience4j.ratelimiter.instances.refundsApi.limit-refresh-period=1h",
    "resilience4j.ratelimiter.instances.refundsApi.timeout-duration=0"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RefundsRateLimiterTest {

    private static final String TOO_MANY_REQUESTS = "Too many requests";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean
    private RefundReasonRepository refundReasonRepository;

    @MockBean
    private StatusHistoryRepository statusHistoryRepository;

    @MockBean
    private RejectionReasonRepository rejectionReasonRepository;

    @MockBean
    private ContextStartListener contextStartListener;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @MockBean
    private LDClientInterface ldClient;

    @MockBean
    private RefundNotificationService refundNotificationService;

    @MockBean
    private IacService iacService;

    @MockBean
    private NotificationServiceImpl notificationService;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private Specification<Refund> mockSpecification;

    @MockBean
    @Qualifier("restTemplateIdam")
    private RestTemplate restTemplateIdam;

    @MockBean
    @Qualifier("restTemplatePayment")
    private RestTemplate restTemplatePayment;

    @MockBean
    @Qualifier("restTemplateNotify")
    private RestTemplate restTemplateNotify;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void shouldRateLimitRefundsControllerEndpoints() throws Exception {
        when(refundReasonRepository.findAll()).thenReturn(Collections.singletonList(getRefundReason()));

        mockMvc.perform(get("/refund/reasons").header("Authorization", "Bearer user-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/refund/reasons").header("Authorization", "Bearer user-token"))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().string(TOO_MANY_REQUESTS));
    }

    @Test
    void shouldRateLimitRefundsActionControllerEndpoints() throws Exception {
        when(refundsRepository.findByPaymentReference(anyString()))
            .thenReturn(Optional.of(Collections.singletonList(getRefund())));

        mockMvc.perform(patch("/payment/{paymentReference}/action/cancel", "RC-1111-2222-3333-4444"))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/payment/{paymentReference}/action/cancel", "RC-1111-2222-3333-4444"))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().string(TOO_MANY_REQUESTS));
    }

    private RefundReason getRefundReason() {
        return RefundReason.refundReasonWith()
            .code("RR031")
            .description("No comments")
            .name("Other - divorce")
            .build();
    }

    private Refund getRefund() {
        return Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .reason("RR0001")
            .reference("RF-1628-5241-9956-2215")
            .paymentReference("RC-1628-5241-9956-2315")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .updatedBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .feeIds("50")
            .statusHistories(Collections.singletonList(StatusHistory.statusHistoryWith()
                .id(1)
                .status(RefundStatus.SENTFORAPPROVAL.getName())
                .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                .notes("Refund initiated and sent to team leader")
                .build()))
            .build();
    }
}
