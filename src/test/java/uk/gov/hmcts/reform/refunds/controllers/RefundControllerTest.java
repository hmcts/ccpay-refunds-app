package uk.gov.hmcts.reform.refunds.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.*;
import uk.gov.hmcts.reform.refunds.model.*;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonsRepository;
import uk.gov.hmcts.reform.refunds.services.IdamServiceImpl;
import uk.gov.hmcts.reform.refunds.services.RefundsServiceImpl;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SUBMITTED;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RefundControllerTest {

    private static final String REFUND_REFERENCE_REGEX = "^[RF-]{3}(\\w{4}-){3}(\\w{4})";

    private RefundReason refundReason = RefundReason.refundReasonWith().
        code("RR002")
        .description("No comments")
        .name("reason1")
        .build();
    private IdamUserIdResponse mockIdamUserIdResponse = IdamUserIdResponse.idamUserIdResponseWith()
        .familyName("VP")
        .givenName("VP")
        .name("VP")
        .sub("V_P@gmail.com")
        .roles(Arrays.asList("vp"))
        .uid("986-erfg-kjhg-123")
        .build();
    private Refund refund = Refund.refundsWith()
        .amount(new BigDecimal(100))
        .paymentReference("RC-1111-2222-3333-4444")
        .reason("test-123")
        .refundStatus(SUBMITTED)
        .reference("RF-1234-1234-1234-1234")
        .build();
    private ObjectMapper mapper = new ObjectMapper();
    private RefundRequest refundRequest = RefundRequest.refundRequestWith()
        .paymentReference("RC-1234-1234-1234-1234")
        .refundAmount(new BigDecimal(100))
        .refundReason("RR002")
        .ccdCaseNumber("1111222233334444")
        .build();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Mock
    private RefundsServiceImpl refundsService;
    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean
    private RejectionReasonsRepository rejectionReasonsRepository;

    @Mock
    private IdamServiceImpl idamService;
    @InjectMocks
    private RefundsController refundsController;
    @Mock
    private ReferenceUtil referenceUtil;
    @Mock
    private RefundReasonRepository refundReasonRepository;
    @MockBean
    @Qualifier("restTemplateIdam")
    private RestTemplate restTemplateIdam;

    @MockBean
    @Qualifier("restTemplatePayment")
    private RestTemplate restTemplatePayment;

    @MockBean
    private OAuth2RestOperations restOperations;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void getRefundReasonsList() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/refund/reasons")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("RR001"))
            .andExpect(jsonPath("$[0].name").value("Duplicate Payment"))
            .andReturn();

        List<RefundReason> refundReasonList = mapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );
        assertEquals(4, refundReasonList.size());
    }

    @Test
    void createRefund() throws Exception {

        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(Collections.emptyList()));

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        MvcResult result = mockMvc.perform(post("/refund")
                                               .content(asJsonString(refundRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        ObjectMapper mapper = new ObjectMapper();
        RefundResponse refundResponse = mapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<>() {
            }

        );
        assertTrue(refundResponse.getRefundReference().matches(REFUND_REFERENCE_REGEX));

    }


    @Test
    void createRefundWithOtherReason() throws Exception {

        refundRequest.setRefundReason("RR004-Other");

        List<Refund> refunds = Collections.emptyList();
        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(refunds));

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        MvcResult result = mockMvc.perform(post("/refund")
                                               .content(asJsonString(refundRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        RefundResponse refundResponse = mapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );
        assertTrue(refundResponse.getRefundReference().matches(REFUND_REFERENCE_REGEX));

    }

    @Test
    void createRefundReturns400ForAlreadyRefundedPaymentReference() throws Exception {

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);

        Refund refund = Refund.refundsWith()
            .amount(refundRequest.getRefundAmount())
            .paymentReference(refundRequest.getPaymentReference())
            .reason(refundReasonRepository.findByCodeOrThrow(refundRequest.getRefundReason()).getCode())
            .refundStatus(SUBMITTED)
            .reference(referenceUtil.getNext("RF"))
            .build();

        List<Refund> refunds = Collections.singletonList(refund);
        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(refunds));

        MvcResult result = mockMvc.perform(post("/refund")
                                               .content(asJsonString(refundRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();

        String ErrorMessage = result.getResponse().getContentAsString();
        assertTrue(ErrorMessage.equals("Refund is already processed for this payment"));
    }


    @Test
    void createRefundReturns504ForGatewayTimeout() throws Exception {

        List<Refund> refunds = Collections.emptyList();
        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(refunds));

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenThrow(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT));

        MvcResult result = mockMvc.perform(post("/refund")
                                               .content(asJsonString(refundRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isGatewayTimeout())
            .andReturn();

        String ErrorMessage = result.getResponse().getContentAsString();
        assertTrue(ErrorMessage.equals("Unable to retrieve User information. Please try again later"));
    }


    @Test
    public void approveRefundRequestReturnsSuccessResponse() throws Exception{
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));
        doReturn(ResponseEntity.ok(Optional.of(ReconciliationProviderResponse.buildReconciliationProviderResponseWith()
                                                     .amount(BigDecimal.valueOf(100))
                                                     .refundReference("RF-1628-5241-9956-2215")
                                                     .build()
        ))).when(restOperations).exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());

        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","APPROVE")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                               .andExpect(status().isCreated())
                                               .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Refund request reviewed successfully");
    }

    @Test
    public void anyRefundReviewActionOnUnSubmittedRefundReturnsBadRequest() throws Exception{
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");
        Refund unsubmittedRefund = getRefund();
        unsubmittedRefund.setRefundStatus(RefundStatus.SENTBACK);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(unsubmittedRefund));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","APPROVE")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                                .andExpect(status().isBadRequest())
                                                .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Refund is not submitted");
    }

    @Test
    public void rejectRefundRequestReturnsSuccessResponse() throws Exception{
        RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
                                                    .code("RR0001")
                                                    .build();
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());
        when(rejectionReasonsRepository.findByCode(anyString())).thenReturn(Optional.of(RejectionReason
                                                                                            .rejectionReasonWith()
                                                                                            .code("RR0001")
                                                                                            .name("rejection name")
                                                                                            .build()
                                                                                            ));
        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","REJECT")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                               .andExpect(status().isCreated())
                                               .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Refund request reviewed successfully");
    }

    @Test
    public void sendbackRefundRequestReturnsSuccessResponse() throws Exception{
        RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
                                                    .reason("send back reason")
                                                    .build();
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());
        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","SENDBACK")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                                .andExpect(status().isCreated())
                                                .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Refund request reviewed successfully");
    }


    @Test
    public void rejectRefundRequestWithoutReasonCodeReturnsBadRequest() throws Exception{
        RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
                                                    .reason("reason")
                                                    .build();
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","REJECT")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                                .andExpect(status().isBadRequest())
                                                .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Refund reject reason is required");
    }

    @Test
    public void rejectRefundRequestWithOthersCodeAndWithoutReasonReturnsBadRequest() throws Exception{
        RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
                                                    .code("RE005")
                                                    .build();
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","REJECT")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                               .andExpect(status().isBadRequest())
                                               .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Refund reject reason is required for others");
    }

    @Test
    public void rejectRefundRequestWithOthersCodeAndWithReasonReturnsSuccessResponse() throws  Exception {
        RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
                                                    .code("RE005")
                                                    .reason("custom reason")
                                                    .build();
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());
        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","REJECT")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                               .andExpect(status().isCreated())
                                               .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Refund request reviewed successfully");
    }

    @Test
    public void sendBackRefundRequestWithoutReasonReturnsBadRequest() throws  Exception {
        RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
                                                    .code("RR002")
                                                    .build();
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());
        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","SENDBACK")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                               .andExpect(status().isBadRequest())
                                               .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Enter reason for sendback");

    }

    @Test
    public void approveRefundRequestForNotExistingPaymentReturnsBadRequest() throws Exception {
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR001","reason1");
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        when(authTokenGenerator.generate()).thenReturn("service auth token");

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).
            thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","APPROVE")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                               .andExpect(status().isNotFound())
                                               .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Payment Reference not found");
    }

    @Test
    public void approveRefundRequestPaymentServerIsUnAvailableReturnsInternalServerError() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).
            thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","APPROVE")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                                .andExpect(status().isInternalServerError())
                                                .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Payment Server Exception");
    }

    @Test
    public void approveRefundRequestWhenSendingMalformedRequestToPaymentReturnsBadRequest() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).
            thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","APPROVE")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                                .andExpect(status().isBadRequest())
                                                .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Invalid Request: Payhub");
    }

    @Test
    public void approveRefundRequestWhenSendingInvalidRequestToReconciliationProviderReturnsBadRequest() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(restOperations.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class))).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","APPROVE")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                                .andExpect(status().isBadRequest())
                                                .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Invalid Request: Reconciliation Provider");
    }

    @Test
    public void approveRefundRequestWhenReconciliationProviderIsUnavailableReturnsInternalServerError() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(restOperations.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class))).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","APPROVE")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                                .andExpect(status().isInternalServerError())
                                                .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Reconciliation Provider Server Exception");
    }

    @Test
    public void approveRefundRequest_WhenRefundIsNotAvailable() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.ofNullable(null));

        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","APPROVE")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
                                            .andExpect(status().isNotFound())
                                            .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Refunds not found for RF-1628-5241-9956-2215");
    }



    private Refund getRefund(){
        return  Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .reason("RR0001")
            .reference("RF-1628-5241-9956-2215")
            .paymentReference("RC-1628-5241-9956-2315")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .refundStatus(SUBMITTED)
            .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .updatedBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                                               .id(1)
                                               .status("submitted")
                                               .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                                               .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                                               .notes("Refund Initiated")
                                               .build()))
            .build();
    }

    private IdamUserIdResponse getIdamResponse(){
         return IdamUserIdResponse.idamUserIdResponseWith()
            .familyName("VP")
            .givenName("VP")
            .name("VP")
            .sub("V_P@gmail.com")
            .roles(Arrays.asList("vp"))
            .uid("986-erfg-kjhg-123")
            .build();
    }



    @Test
    void getRejectionReasonsList() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/refund/rejection-reasons")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        ObjectMapper mapper = new ObjectMapper();
        List<String> rejectionReasonList = mapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );
        assertEquals(5, rejectionReasonList.size());
    }

    private PaymentGroupResponse getPaymentGroupDto(){
        return  PaymentGroupResponse.paymentGroupDtoWith()
            .paymentGroupReference("payment-group-reference")
            .dateCreated(Date.from(Instant.now()))
            .dateUpdated(Date.from(Instant.now()))
            .payments(Arrays.asList(
                PaymentResponse.paymentResponseWith()
                    .amount(BigDecimal.valueOf(100))
                    .description("description")
                    .reference("RC-1628-5241-9956-2315")
                    .dateCreated(Date.from(Instant.now()))
                    .dateUpdated(Date.from(Instant.now()))
                    .currency(CurrencyCode.GBP)
                    .caseReference("case-reference")
                    .ccdCaseNumber("ccd-case-number")
                    .channel("solicitors portal")
                    .method("payment by account")
                    .externalProvider("provider")
                    .accountNumber("PBAFUNC1234")
                    .paymentAllocation(Arrays.asList(
                        PaymentAllocationResponse.paymentAllocationDtoWith()
                            .allocationStatus("allocationStatus")
                            .build()
                    ))
                    .build()
            ))
            .remissions(Arrays.asList(
                RemissionResponse.remissionDtoWith()
                    .remissionReference("remission-reference")
                    .beneficiaryName("ben-ten")
                    .ccdCaseNumber("ccd-case-number")
                    .caseReference("case-reference")
                    .hwfReference("hwf-reference")
                    .hwfAmount(BigDecimal.valueOf(100))
                    .dateCreated(Date.from(Instant.now()))
                    .build()
            ))
            .fees(Arrays.asList(
                PaymentFeeResponse.feeDtoWith()
                    .code("FEE012")
                    .feeAmount(BigDecimal.valueOf(100))
                    .calculatedAmount(BigDecimal.valueOf(100))
                    .netAmount(BigDecimal.valueOf(100))
                    .version("1")
                    .volume(1)
                    .feeAmount(BigDecimal.valueOf(100))
                    .ccdCaseNumber("ccd-case-number")
                    .reference("reference")
                    .memoLine("memo-line")
                    .naturalAccountCode("natural-account-code")
                    .description("description")
                    .allocatedAmount(BigDecimal.valueOf(100))
                    .apportionAmount(BigDecimal.valueOf(100))
                    .dateCreated(Date.from(Instant.now()))
                    .dateUpdated(Date.from(Instant.now()))
                    .dateApportioned(Date.from(Instant.now()))
                    .amountDue(BigDecimal.valueOf(0))
                    .build()
            )).build();
    }
}
