package uk.gov.hmcts.reform.refunds.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.IdamServiceImpl;
import uk.gov.hmcts.reform.refunds.services.RefundReviewService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefundsActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private RefundReviewService refundReviewService;


    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean
    private ContextStartListener contextStartListener;
    @Mock
    private IdamServiceImpl idamService;

    @InjectMocks
    private RefundsActionController refundsActionController;

    @MockBean
    @Qualifier("restTemplateIdam")
    private RestTemplate restTemplateIdam;

    @Value("${idam.api.url}")
    private String idamBaseUrl;
    @Mock
    private MultiValueMap<String, String> map;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ObjectMapper objectMapper;

    private RejectionReason getRejectionReason() {
        return RejectionReason
                .rejectionReasonWith()
                .code("RR0001")
                .name("rejection name")
                .build();
    }

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }


    //@Test
    void givenPaymentReference_whenCancelRefunds_thenRefundsAreCancelled() throws Exception {

        List<Refund> refunds = Collections.singletonList(getRefund());
        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(refunds));

        MvcResult mvcResult = mockMvc.perform(patch(
                "/payment/{paymentReference}/action/cancel",
                "RC-1111-2222-3333-4444"))
                .andExpect(status().isOk())
                .andReturn();

        String message = mvcResult.getResponse().getContentAsString();
        assertEquals("Refund cancelled", message);
    }


    //@Test
    void givenPaymentReference_whenCancelRefunds_thenRefundNotFoundException() throws Exception {

        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(Collections.emptyList()));

        MvcResult mvcResult = mockMvc.perform(patch(
                "/payment/{paymentReference}/action/cancel",
                "RC-1111-2222-3333-4444"))
                .andExpect(status().isNotFound())
                .andReturn();

        String errorMessage = mvcResult.getResponse().getContentAsString();
        assertEquals("Refunds not found for payment reference RC-1111-2222-3333-4444", errorMessage);
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
                .refundStatus(uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL)
                .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                .updatedBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                .feeIds("50")
                .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                        .id(1)
                        .status(RefundStatus.SENTFORAPPROVAL.getName())
                        .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                        .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                        .notes("Refund initiated and sent to team leader")
                        .build()))
                .build();
    }
}
