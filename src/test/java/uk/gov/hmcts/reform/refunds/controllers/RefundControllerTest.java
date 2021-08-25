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
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.*;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderServerException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.services.IdamServiceImpl;
import uk.gov.hmcts.reform.refunds.services.RefundsServiceImpl;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.ACCEPTED;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTBACK;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTTOMIDDLEOFFICE;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.GET_REFUND_LIST_CCD_CASE_USER_ID;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.refundListSupplierBasedOnCCDCaseNumber;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.refundListSupplierForSendBackStatus;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.refundListSupplierForSubmittedStatus;
import static uk.gov.hmcts.reform.refunds.services.IdamServiceImpl.USERID_ENDPOINT;
import static uk.gov.hmcts.reform.refunds.services.IdamServiceImpl.USER_FULL_NAME_ENDPOINT;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RefundControllerTest {

    public static final Supplier<IdamFullNameRetrivalResponse[]> idamFullNameCCDSearchRefundListSupplier = () -> new IdamFullNameRetrivalResponse[]{IdamFullNameRetrivalResponse
        .idamFullNameRetrivalResponseWith()
        .id(GET_REFUND_LIST_CCD_CASE_USER_ID)
        .email("mockfullname@gmail.com")
        .forename("mock-Forename")
        .surname("mock-Surname")
        .roles(List.of("Refund-approver", "Refund-admin"))
        .build()};
    public static final Supplier<IdamFullNameRetrivalResponse[]> idamFullNameSubmittedRefundListSupplier = () -> new IdamFullNameRetrivalResponse[]{IdamFullNameRetrivalResponse
        .idamFullNameRetrivalResponseWith()
        .id(GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)
        .email("mock1fullname@gmail.com")
        .forename("mock1-Forename")
        .surname("mock1-Surname")
        .roles(List.of("Refund-approver", "Refund-admin"))
        .build()};

    public static final Supplier<IdamFullNameRetrivalResponse[]> idamFullNameSendBackRefundListSupplier = () -> new IdamFullNameRetrivalResponse[]{IdamFullNameRetrivalResponse
        .idamFullNameRetrivalResponseWith()
        .id(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
        .email("mock2fullname@gmail.com")
        .forename("mock2-Forename")
        .surname("mock2-Surname")
        .roles(List.of("Refund-approver", "Refund-admin"))
        .build()};
    public static final Supplier<IdamUserIdResponse> idamUserIDResponseSupplier = () -> IdamUserIdResponse.idamUserIdResponseWith()
        .familyName("mock-Surname")
        .givenName("mock-Surname")
        .name("mock-ForeName")
        .sub("mockfullname@gmail.com")
        .roles(List.of("Refund-approver", "Refund-admin"))
        .uid(GET_REFUND_LIST_CCD_CASE_USER_ID)
        .build();
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
        .refundStatus(SENTFORAPPROVAL)
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
    private RejectionReasonRepository rejectionReasonRepository;

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
    @Value("${idam.api.url}")
    private String idamBaseURL;
    @Mock
    private MultiValueMap<String, String> map;

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

    @Autowired
    private ObjectMapper objectMapper;

    private static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RejectionReason getRejectionReason() {
        return RejectionReason
            .rejectionReasonWith()
            .code("RR0001")
            .name("rejection name")
            .build();
    }

    @BeforeEach
    public void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }


    @Test
    public void testRefundListBasedOnCCDCaseNumber() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockIdamFullNameCall(GET_REFUND_LIST_CCD_CASE_USER_ID, idamFullNameCCDSearchRefundListSupplier.get());

        //mock repository call
        when(refundsRepository.findByCcdCaseNumber(GET_REFUND_LIST_CCD_CASE_USER_ID))
            .thenReturn(Optional.ofNullable(List.of(
                refundListSupplierBasedOnCCDCaseNumber.get())));

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "submitted")
                                                  .queryParam("ccdCaseNumber", GET_REFUND_LIST_CCD_CASE_USER_ID)
                                                  .queryParam("excludeCurrentUser", " ")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();

        RefundListDtoResponse refundListDtoResponse = mapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(1, refundListDtoResponse.getRefundList().size());
        assertEquals("mock-Forename mock-Surname", refundListDtoResponse.getRefundList().get(0).getUserFullName());
        assertEquals(SENTFORAPPROVAL, refundListDtoResponse.getRefundList().get(0).getRefundStatus());
    }

    @Test
    public void testRefundListForSubmittedStatus() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockIdamFullNameCall(GET_REFUND_LIST_CCD_CASE_USER_ID, idamFullNameCCDSearchRefundListSupplier.get());

        //mock repository call
        when(refundsRepository.findByRefundStatusAndCreatedByIsNot(
            SENTFORAPPROVAL,
            GET_REFUND_LIST_CCD_CASE_USER_ID
        ))
            .thenReturn(Optional.ofNullable(List.of(
                refundListSupplierBasedOnCCDCaseNumber.get())));

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "sent for approval")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("excludeCurrentUser", " ")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();

        RefundListDtoResponse refundListDtoResponse = mapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(1, refundListDtoResponse.getRefundList().size());
        assertEquals("mock-Forename mock-Surname", refundListDtoResponse.getRefundList().get(0).getUserFullName());
        assertEquals(SENTFORAPPROVAL, refundListDtoResponse.getRefundList().get(0).getRefundStatus());
    }

    @Test
    public void testMultipleRefundsSubmittedStatus() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockIdamFullNameCall(GET_REFUND_LIST_CCD_CASE_USER_ID, idamFullNameCCDSearchRefundListSupplier.get());
        mockIdamFullNameCall(
            GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID,
            idamFullNameSubmittedRefundListSupplier.get()
        );

        //mock repository call
        when(refundsRepository.findByRefundStatus(
            SENTFORAPPROVAL
        ))
            .thenReturn(Optional.ofNullable(List.of(
                refundListSupplierBasedOnCCDCaseNumber.get(), refundListSupplierForSubmittedStatus.get())));

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "sent for approval")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("excludeCurrentUser", "false")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();

        RefundListDtoResponse refundListDtoResponse = mapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(2, refundListDtoResponse.getRefundList().size());
        assertEquals(SENTFORAPPROVAL, refundListDtoResponse.getRefundList().get(0).getRefundStatus());

        assertTrue(refundListDtoResponse.getRefundList().stream()
                       .anyMatch(refundListDto -> refundListDto.getUserFullName().equalsIgnoreCase(
                           "mock-Forename mock-Surname")));

        assertTrue(refundListDtoResponse.getRefundList().stream()
                       .anyMatch(refundListDto -> refundListDto.getUserFullName().equalsIgnoreCase(
                           "mock1-Forename mock1-Surname")));
    }

    @Test
    public void testRefundsListSendBackStatus() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockIdamFullNameCall(
            GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID,
            idamFullNameSendBackRefundListSupplier.get()
        );

        //mock repository call
        when(refundsRepository.findByRefundStatus(
            SENTBACK
        ))
            .thenReturn(Optional.ofNullable(List.of(
                refundListSupplierForSendBackStatus.get())));

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "sent back")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("excludeCurrentUser", "false")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();

        RefundListDtoResponse refundListDtoResponse = mapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(1, refundListDtoResponse.getRefundList().size());
        assertEquals(SENTBACK, refundListDtoResponse.getRefundList().get(0).getRefundStatus());
        assertEquals("mock2-Forename mock2-Surname", refundListDtoResponse.getRefundList().get(0).getUserFullName());
    }

    public void mockIdamFullNameCall(String userId,
                                     IdamFullNameRetrivalResponse[] idamFullNameRetrivalResponse) {
        UriComponentsBuilder builderCCDSearchURI = UriComponentsBuilder.fromUriString(idamBaseURL + USER_FULL_NAME_ENDPOINT)
            .queryParam("query", "id:" + userId);
        ResponseEntity<IdamFullNameRetrivalResponse[]> responseForFullNameCCDUserId =
            new ResponseEntity<>(idamFullNameRetrivalResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(eq(builderCCDSearchURI.toUriString())
            , any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamFullNameRetrivalResponse[].class)
        )).thenReturn(responseForFullNameCCDUserId);
    }

    public void mockUserinfoCall(IdamUserIdResponse idamUserIdResponse) {
        UriComponentsBuilder builderForUserInfo = UriComponentsBuilder.fromUriString(idamBaseURL + USERID_ENDPOINT);
        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(idamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(
            eq(builderForUserInfo.toUriString()),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
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
            .refundStatus(SENTFORAPPROVAL)
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
        unsubmittedRefund.setRefundStatus(SENTBACK);
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
        when(rejectionReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RejectionReason
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

    @Test
    public void approveRefundRequestWithRetrospectiveRemissionReturnsSuccessResponse() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");

        Refund refundWithRetroRemission = getRefund();
        refundWithRetroRemission.setReason("RR004-Retrospective Remission");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(refundWithRetroRemission));

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
    public void anyActionOnRetroSpectiveRefundWithoutRemissionSendsInternalServerError() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");

        Refund refundWithRetroRemission = getRefund();
        refundWithRetroRemission.setReason("RR004-Retrospective Remission");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(refundWithRetroRemission));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        PaymentGroupResponse paymentGroupResponse = getPaymentGroupDto();
        paymentGroupResponse.setRemissions(Arrays.asList());

        when(restTemplatePayment.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(paymentGroupResponse)

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
                                                .andExpect(status().isInternalServerError())
                                                .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Remission not found");
    }

    @Test
    public void anyActionOnRetroSpectiveRefundWithDifferentAmountThanRemissionSendsInternalServerError() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");

        Refund refundWithRetroRemission = getRefund();
        refundWithRetroRemission.setReason("RR004-Retrospective Remission");
        refundWithRetroRemission.setAmount(BigDecimal.valueOf(10));
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(refundWithRetroRemission));

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
            .andExpect(status().isInternalServerError())
            .andReturn();
        assertEquals(result.getResponse().getContentAsString(),"Remission amount not equal to refund amount");
    }

    @Test
    public void approveRefundRequestWhenReconciliationProviderThrowsServerExceptionItSendsInternalServerError() throws Exception {

        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001","reason1");
        Refund refundWithRetroRemission = getRefund();
        refundWithRetroRemission.setReason("RR004-Retrospective Remission");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(refundWithRetroRemission));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        when(restTemplatePayment.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));
        doReturn(new ResponseEntity(HttpStatus.PERMANENT_REDIRECT)).when(restOperations).exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));

        MvcResult result = mockMvc.perform(patch("/refund/{reference}/action/{reviewer-action}","RF-1628-5241-9956-2215","APPROVE")
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andReturn();
        assertEquals("Reconciliation Provider: Permanent Redirect",result.getResponse().getContentAsString());
    }

    @Test
    public void retrieveActionsForSubmittedState() throws Exception {
        refund.setRefundStatus(SENTFORAPPROVAL);
        when(refundsRepository.findByReferenceOrThrow(any())).thenReturn(refund);
        mockMvc.perform(get("/refunds/RF-1234-1234-1234-1234/actions")
                            .header("Authorization", "user")
                            .header("ServiceAuthorization", "service")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$.[0].code").value("Approve"))
            .andExpect(jsonPath("$.[0].label").value("Send to middle office"))
            .andExpect(jsonPath("$.[1].code").value("Reject"))
            .andExpect(jsonPath("$.[2].code").value("Return to caseworker"));
    }

    @Test
    public void retrieveActionsForNeedMoreInfoState() throws Exception {
        refund.setRefundStatus(SENTBACK);
        when(refundsRepository.findByReferenceOrThrow(any())).thenReturn(refund);
        mockMvc.perform(get("/refunds/RF-1234-1234-1234-1233/actions")
                            .header("Authorization", "user")
                            .header("ServiceAuthorization", "service")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$.[0].code").value("Submit"))
            .andExpect(jsonPath("$.[0].label").value("Send for approval"))
            .andExpect(jsonPath("$.[1].code").value("Cancel"));
    }

    @Test
    public void retrieveActionsForAcceptedState() throws Exception {
        refund.setRefundStatus(ACCEPTED);
        when(refundsRepository.findByReferenceOrThrow(any())).thenReturn(refund);
        mockMvc.perform(get("/refunds/RF-1234-1234-1234-1231/actions")
                            .header("Authorization", "user")
                            .header("ServiceAuthorization", "service")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$").value("No actions to proceed further"));
    }


    @Test
    public void retrieveActionsForApprovedState() throws Exception {
        refund.setRefundStatus(SENTTOMIDDLEOFFICE);
        when(refundsRepository.findByReferenceOrThrow(any())).thenReturn(refund);
        mockMvc.perform(get("/refunds/RF-1234-1234-1234-1234/actions")
                            .header("Authorization", "user")
                            .header("ServiceAuthorization", "service")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$.[0].code").value("Accept"))
            .andExpect(jsonPath("$.[0].label").value("Refund request accepted"))
            .andExpect(jsonPath("$.[1].code").value("Reject"))
            .andExpect(jsonPath("$.[1].label").value("There is no refund due"));
    }


    @Test
    void getRejectionReasonsList() throws Exception {
        when(rejectionReasonRepository.findAll()).thenReturn(Arrays.asList(getRejectionReason()));
        MvcResult mvcResult = mockMvc.perform(get("/refund/rejection-reasons")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        ObjectMapper mapper = new ObjectMapper();
        List<RejectionReasonResponse> rejectionReasonList = mapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );
        assertEquals(1, rejectionReasonList.size());
    }

    @Test
    public void UpdateRefundStatusAccepted() throws Exception {
        RefundStatusUpdateRequest refundStatusUpdateRequest = RefundStatusUpdateRequest.RefundRequestWith().status(
            RefundStatus.ACCEPTED).build();
        refund.setRefundStatus(SENTTOMIDDLEOFFICE);
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}",
            "RF-1234-1234-1234-1234"
        )
                                               .content(asJsonString(refundStatusUpdateRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent())
            .andReturn();

        assertTrue(result.getResponse().getContentAsString().equals("Refund status updated successfully"));

    }

    @Test
    public void UpdateRefundStatusRejected() throws Exception {
        RefundStatusUpdateRequest refundStatusUpdateRequest = new RefundStatusUpdateRequest(
            "Refund rejected",
            RefundStatus.REJECTED
        );
        refund.setRefundStatus(SENTTOMIDDLEOFFICE);
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}",
            "RF-1234-1234-1234-1234"
        )
                                               .content(asJsonString(refundStatusUpdateRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent())
            .andReturn();

        assertTrue(result.getResponse().getContentAsString().equals("Refund status updated successfully"));
    }

    @Test
    public void UpdateRefundStatusRejectedWithOutReason() throws Exception {
        RefundStatusUpdateRequest refundStatusUpdateRequest = RefundStatusUpdateRequest.RefundRequestWith()
            .status(RefundStatus.REJECTED).build();
        refund.setRefundStatus(SENTTOMIDDLEOFFICE);

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}",
            "RF-1234-1234-1234-1234"
        )
                                               .content(asJsonString(refundStatusUpdateRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();

        ErrorResponse errorResponse = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            ErrorResponse.class
        );


        assertEquals(
            "Refund status should be ACCEPTED or REJECTED/Refund rejection reason is missing",
            errorResponse.getDetails().get(0)
        );
    }

    @Test
    public void UpdateRefundStatusNotAllowedWithCurrentStatus() throws Exception {
        RefundStatusUpdateRequest refundStatusUpdateRequest = new RefundStatusUpdateRequest(
            "Refund rejected",
            RefundStatus.REJECTED
        );
        refund.setRefundStatus(SENTFORAPPROVAL);
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}",
            "RF-1234-1234-1234-1234"
        )
                                               .content(asJsonString(refundStatusUpdateRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertTrue(result.getResponse().getContentAsString().equals("Action not allowed to proceed"));

    }

    private PaymentGroupResponse getPaymentGroupDto() {
        return PaymentGroupResponse.paymentGroupDtoWith()
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
                    .feeId(50)
                    .build()
            ))
            .fees(Arrays.asList(
                PaymentFeeResponse.feeDtoWith()
                    .id(50)
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


    private Refund getRefund() {
        return Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .reason("RR0001")
            .reference("RF-1628-5241-9956-2215")
            .paymentReference("RC-1628-5241-9956-2315")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .refundStatus(SENTFORAPPROVAL)
            .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .updatedBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                                               .id(1)
                                               .status(SENTFORAPPROVAL.getName())
                                               .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                                               .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                                               .notes("Refund Initiated")
                                               .build()))
            .build();
    }

    private IdamUserIdResponse getIdamResponse() {
        return IdamUserIdResponse.idamUserIdResponseWith()
            .familyName("VP")
            .givenName("VP")
            .name("VP")
            .sub("V_P@gmail.com")
            .roles(Arrays.asList("vp"))
            .uid("986-erfg-kjhg-123")
            .build();
    }
}
