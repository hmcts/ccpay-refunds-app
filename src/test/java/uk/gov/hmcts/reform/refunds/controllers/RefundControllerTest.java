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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
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
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.services.IdamServiceImpl;
import uk.gov.hmcts.reform.refunds.services.RefundsServiceImpl;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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
class RefundControllerTest {

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
        code("RR031")
        .description("No comments")
        .name("Other - divorce")
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
        .feeIds("1")
        .refundStatus(SENTFORAPPROVAL)
        .reference("RF-1234-1234-1234-1234")
        .build();
    private ObjectMapper mapper = new ObjectMapper();
    private RefundRequest refundRequest = RefundRequest.refundRequestWith()
        .paymentReference("RC-1234-1234-1234-1234")
        .refundAmount(new BigDecimal(100))
        .refundReason("RR002")
        .ccdCaseNumber("1111222233334444")
        .feeIds("1")
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
    private StatusHistoryRepository statusHistoryRepository;
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
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }


    @Test
    void testRefundListBasedOnCCDCaseNumber() throws Exception {

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
        when(refundsRepository.findByRefundStatus(
            SENTFORAPPROVAL
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
            mvcResult.getResponse().getContentAsString(), RefundListDtoResponse.class
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(1, refundListDtoResponse.getRefundList().size());
        assertEquals("mock-Forename mock-Surname", refundListDtoResponse.getRefundList().get(0).getUserFullName());
        assertEquals(SENTFORAPPROVAL, refundListDtoResponse.getRefundList().get(0).getRefundStatus());
    }


    @Test
    void testInvalidInputException() throws  Exception {
        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("excludeCurrentUser", "")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest()).andReturn();

        String ErrorMessage = mvcResult.getResponse().getContentAsString();
        assertTrue(ErrorMessage.equals("Please provide criteria to fetch refunds i.e. Refund status or ccd case number"));

    }

    @Test
    void testInvalidRefundReasonException() throws  Exception {
        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "Invalid status")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("excludeCurrentUser", "")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest()).andReturn();

        String ErrorMessage = mvcResult.getResponse().getContentAsString();
        assertTrue(ErrorMessage.equals("Invalid Refund status"));

    }

    @Test
    void testMultipleRefundsSubmittedStatus() throws Exception {

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
    void testRefundsListSendBackStatus() throws Exception {

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
            .andExpect(jsonPath("$[0].name").value("Amended claim"))
            .andReturn();

        List<RefundReason> refundReasonList = mapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );
        assertEquals(35, refundReasonList.size());
    }

    @Test
    void createRefund() throws Exception {

        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(Collections.emptyList()));

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(RefundReason.refundReasonWith()
                                                                                    .code("RR002")
                                                                                    .name("Amended court")
                                                                                   .build());

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
    void createRefundWithOtherCodeWithoutReason() throws Exception {

        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(Collections.emptyList()));

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(RefundReason.refundReasonWith()
                                                                                   .code("RR031")
                                                                                   .name("Other - Tribunals")
                                                                                   .build());

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        RefundRequest refundRequest = RefundRequest.refundRequestWith()
            .paymentReference("RC-1234-1234-1234-1234")
            .refundAmount(new BigDecimal(100))
            .refundReason("RR031")
            .ccdCaseNumber("1111222233334444")
            .feeIds("1")
            .build();

        MvcResult result = mockMvc.perform(post("/refund")
                                               .content(asJsonString(refundRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
    }


    @Test
    void createRefundWithOtherReason() throws Exception {

        refundRequest.setRefundReason("RR031-Other");

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

        assertEquals(
            "Unable to retrieve User information. Please try again later",
            result.getResponse().getContentAsString()
        );
    }


    @Test
    void approveRefundRequestReturnsSuccessResponse() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));
        doReturn(ResponseEntity.ok(Optional.of(ReconciliationProviderResponse.buildReconciliationProviderResponseWith()
                                                   .amount(BigDecimal.valueOf(100))
                                                   .refundReference("RF-1628-5241-9956-2215")
                                                   .build()
        ))).when(restOperations).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();
        assertEquals("Refund approved", result.getResponse().getContentAsString());
    }

    @Test
    public void anyRefundReviewActionOnUnSubmittedRefundReturnsBadRequest() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        Refund unsubmittedRefund = getRefund();
        unsubmittedRefund.setRefundStatus(SENTBACK);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(unsubmittedRefund));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
        assertEquals("Refund is not submitted", result.getResponse().getContentAsString());
    }

    @Test
    void rejectRefundRequestReturnsSuccessResponse() throws Exception {
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
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "REJECT"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();
        assertEquals("Refund rejected", result.getResponse().getContentAsString());
    }

    @Test
    void sendbackRefundRequestReturnsSuccessResponse() throws Exception {
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
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "SENDBACK"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();
        assertEquals("Refund returned to caseworker", result.getResponse().getContentAsString());
    }


    @Test
    void rejectRefundRequestWithoutReasonCodeReturnsBadRequest() throws Exception {
        RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
            .reason("reason")
            .build();
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "REJECT"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
        assertEquals("Refund reject reason is required", result.getResponse().getContentAsString());
    }

    @Test
    void rejectRefundRequestWithOthersCodeAndWithoutReasonReturnsBadRequest() throws Exception {
        RefundReviewRequest refundReviewRequest = RefundReviewRequest.buildRefundReviewRequest()
            .code("RE005")
            .build();
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "REJECT"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
        assertEquals("Refund reject reason is required for others", result.getResponse().getContentAsString());
    }

    @Test
    void rejectRefundRequestWithOthersCodeAndWithReasonReturnsSuccessResponse() throws Exception {
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
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "REJECT"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();
        assertEquals("Refund rejected", result.getResponse().getContentAsString());
    }

    @Test
    void sendBackRefundRequestWithoutReasonReturnsBadRequest() throws Exception {
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
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "SENDBACK"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
        assertEquals("Enter reason for sendback", result.getResponse().getContentAsString());

    }

    @Test
    void approveRefundRequestForNotExistingPaymentReturnsBadRequest() throws Exception {
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR001", "reason1");
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        when(authTokenGenerator.generate()).thenReturn("service auth token");

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).
            thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andReturn();
        assertEquals("Payment Reference not found", result.getResponse().getContentAsString());
    }

    @Test
    void approveRefundRequestPaymentServerIsUnAvailableReturnsInternalServerError() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).
            thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andReturn();
        assertEquals("Payment Server Exception", result.getResponse().getContentAsString());
    }

    @Test
    void approveRefundRequestWhenSendingMalformedRequestToPaymentReturnsBadRequest() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).
            thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
        assertEquals("Invalid Request: Payhub", result.getResponse().getContentAsString());
    }

    @Test
    void approveRefundRequestWhenSendingInvalidRequestToReconciliationProviderReturnsBadRequest() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(restOperations.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class))).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
        assertEquals("Invalid Request: Reconciliation Provider", result.getResponse().getContentAsString());
    }

    @Test
    void approveRefundRequestWhenReconciliationProviderIsUnavailableReturnsInternalServerError() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(restOperations.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class))).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andReturn();
        assertEquals("Reconciliation Provider Server Exception", result.getResponse().getContentAsString());
    }

    @Test
    void approveRefundRequest_WhenRefundIsNotAvailable() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.ofNullable(null));

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andReturn();
        assertEquals("Refunds not found for RF-1628-5241-9956-2215", result.getResponse().getContentAsString());
    }

    @Test
    void approveRefundRequestWithRetrospectiveRemissionReturnsSuccessResponse() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");

        Refund refundWithRetroRemission = getRefund();
        refundWithRetroRemission.setReason("RR004-Retrospective Remission");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(refundWithRetroRemission));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));
        doReturn(ResponseEntity.ok(Optional.of(ReconciliationProviderResponse.buildReconciliationProviderResponseWith()
                                                   .amount(BigDecimal.valueOf(100))
                                                   .refundReference("RF-1628-5241-9956-2215")
                                                   .build()
        ))).when(restOperations).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();
        assertEquals("Refund approved", result.getResponse().getContentAsString());
    }

    @Test
    void anyActionOnRetroSpectiveRefundWithoutRemissionSendsInternalServerError() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");

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

        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(paymentGroupResponse)

        ));
        doReturn(ResponseEntity.ok(Optional.of(ReconciliationProviderResponse.buildReconciliationProviderResponseWith()
                                                   .amount(BigDecimal.valueOf(100))
                                                   .refundReference("RF-1628-5241-9956-2215")
                                                   .build()
        ))).when(restOperations).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andReturn();
        assertEquals("Remission not found", result.getResponse().getContentAsString());
    }

    @Test
    void anyActionOnRetroSpectiveRefundWithDifferentAmountThanRemissionSendsInternalServerError() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");

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


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));
        doReturn(ResponseEntity.ok(Optional.of(ReconciliationProviderResponse.buildReconciliationProviderResponseWith()
                                                   .amount(BigDecimal.valueOf(100))
                                                   .refundReference("RF-1628-5241-9956-2215")
                                                   .build()
        ))).when(restOperations).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andReturn();
        assertEquals("Remission amount not equal to refund amount", result.getResponse().getContentAsString());
    }

    @Test
    void approveRefundRequestWhenReconciliationProviderThrowsServerExceptionItSendsInternalServerError() throws Exception {

        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        Refund refundWithRetroRemission = getRefund();
        refundWithRetroRemission.setReason("RR004-Retrospective Remission");
        when(featureToggler.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(refundWithRetroRemission));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));
        doReturn(new ResponseEntity(HttpStatus.PERMANENT_REDIRECT)).when(restOperations).exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(
                ReconciliationProviderResponse.class)
        );

        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}/action/{reviewer-action}",
            "RF-1628-5241-9956-2215",
            "APPROVE"
        )
                                               .content(asJsonString(refundReviewRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andReturn();
        assertEquals("Reconciliation Provider: Permanent Redirect", result.getResponse().getContentAsString());
    }

    @Test
    void retrieveActionsForSubmittedState() throws Exception {
        refund.setRefundStatus(SENTFORAPPROVAL);
        when(refundsRepository.findByReferenceOrThrow(any())).thenReturn(refund);
        mockMvc.perform(get("/refund/RF-1234-1234-1234-1234/actions")
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
    void retrieveActionsForNeedMoreInfoState() throws Exception {
        refund.setRefundStatus(SENTBACK);
        when(refundsRepository.findByReferenceOrThrow(any())).thenReturn(refund);
        mockMvc.perform(get("/refund/RF-1234-1234-1234-1233/actions")
                            .header("Authorization", "user")
                            .header("ServiceAuthorization", "service")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$.[0].code").value("Submit"))
            .andExpect(jsonPath("$.[0].label").value("Send for approval"))
            .andExpect(jsonPath("$.[1].code").value("Cancel"));
    }

    @Test
    void retrieveActionsForAcceptedState() throws Exception {
        refund.setRefundStatus(ACCEPTED);
        when(refundsRepository.findByReferenceOrThrow(any())).thenReturn(refund);
        mockMvc.perform(get("/refund/RF-1234-1234-1234-1231/actions")
                            .header("Authorization", "user")
                            .header("ServiceAuthorization", "service")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$").value("No actions to proceed further"));
    }


    @Test
    void retrieveActionsForApprovedState() throws Exception {
        refund.setRefundStatus(SENTTOMIDDLEOFFICE);
        when(refundsRepository.findByReferenceOrThrow(any())).thenReturn(refund);
        mockMvc.perform(get("/refund/RF-1234-1234-1234-1234/actions")
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
    void UpdateRefundStatusAccepted() throws Exception {
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

        assertEquals("Refund status updated successfully", result.getResponse().getContentAsString());

    }

    @Test
    void UpdateRefundStatusRejected() throws Exception {
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

        assertEquals("Refund status updated successfully", result.getResponse().getContentAsString());
    }

    @Test
    void UpdateRefundStatusRejectedWithOutReason() throws Exception {
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
    void UpdateRefundStatusNotAllowedWithCurrentStatus() throws Exception {
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

        assertEquals("Action not allowed to proceed", result.getResponse().getContentAsString());

    }

    @Test
    void testGetStatusHistory() {
        // given
        StatusHistoryDto statusHistoryDto = StatusHistoryDto.buildStatusHistoryDtoWith()
            .id(1)
            .refundsId(1)
            .status("AAA")
            .notes("BBB")
            .dateCreated(Timestamp.valueOf("2021-10-10 10:10:10"))
            .createdBy("CCC")
            .build();

        List<StatusHistoryDto> statusHistoryDtoList = new ArrayList<>();
        statusHistoryDtoList.add(statusHistoryDto);

        when(refundsService.getStatusHistory(any(), anyString())).thenReturn(statusHistoryDtoList);

        // when
        ResponseEntity<List<StatusHistoryDto>> result = refundsController.getStatusHistory(null, "reference");

        // then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(200, result.getStatusCodeValue());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals(statusHistoryDto, result.getBody().get(0));
        assertEquals(1, result.getBody().get(0).getId());
        assertEquals(1, result.getBody().get(0).getRefundsId());
        assertEquals("AAA", result.getBody().get(0).getStatus());
        assertEquals("BBB", result.getBody().get(0).getNotes());
        assertEquals(Timestamp.valueOf("2021-10-10 10:10:10"), result.getBody().get(0).getDateCreated());
        assertEquals("CCC", result.getBody().get(0).getCreatedBy());

    }

    @Test
    void testResubmitRefund() throws Exception {

        when(refundsService.resubmitRefund(anyString(), any(), any())).thenReturn(new ResponseEntity(HttpStatus.OK));

        ResponseEntity responseEntity = refundsController.resubmitRefund(null, null, null, null);
        verify(refundsService, times(1)).resubmitRefund(null, null, null);
        assertNull(responseEntity);
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
            .feeIds("50")
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
