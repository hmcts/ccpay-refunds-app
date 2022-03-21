package uk.gov.hmcts.reform.refunds.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundSearchCriteria;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.CurrencyCode;
import uk.gov.hmcts.reform.refunds.dtos.responses.ErrorResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.FeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserInfoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentAllocationResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RejectionReasonResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RemissionResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RerfundLiberataResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ResubmitRefundResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest;
import uk.gov.hmcts.reform.refunds.services.IdamServiceImpl;
import uk.gov.hmcts.reform.refunds.services.NotificationServiceImpl;
import uk.gov.hmcts.reform.refunds.services.PaymentService;
import uk.gov.hmcts.reform.refunds.services.RefundNotificationService;
import uk.gov.hmcts.reform.refunds.services.RefundsServiceImpl;
import uk.gov.hmcts.reform.refunds.utils.DateUtil;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.GET_REFUND_LIST_CCD_CASE_USER_ID1;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("PMD")
class RefundControllerTest {

    public static final Supplier<IdamUserInfoResponse[]> idamFullNameCCDSearchRefundListSupplier =
        () -> new IdamUserInfoResponse[]{IdamUserInfoResponse
            .idamFullNameRetrivalResponseWith()
            .id(GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .email("mockfullname@gmail.com")
            .forename("mock-Forename")
            .surname("mock-Surname")
            .roles(List.of("payments-refund", "payments-refund-approver"))
            .active(true)
            .lastModified("2021-07-20T11:03:08.067Z")
            .build()};
    public static final Supplier<IdamUserInfoResponse[]> idamFullNameCCDSearchRefundListSupplier1 =
        () -> new IdamUserInfoResponse[]{IdamUserInfoResponse
            .idamFullNameRetrivalResponseWith()
            .id(GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .email("mockfullname@gmail.com")
            .forename("mock-Forename")
            .surname("mock-Surname")
            .roles(List.of("payments-refund", "payments-refund-approver"))
            .active(true)
            .lastModified("2021-07-20T11:03:08.067Z")
            .build(),
            IdamUserInfoResponse
                .idamFullNameRetrivalResponseWith()
                .id(RefundServiceImplTest.GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)
                .email("mock1fullname@gmail.com")
                .forename("mock1-Forename")
                .surname("mock1-Surname")
                .roles(List.of("payments-refund", "payments-refund-approver", "caseworker-damage"))
                .active(true)
                .lastModified("2021-07-20T11:03:08.067Z")
                .build()
        };
    public static final Supplier<IdamUserInfoResponse[]> idamFullNameSubmittedRefundListSupplier =
        () -> new IdamUserInfoResponse[]{IdamUserInfoResponse
            .idamFullNameRetrivalResponseWith()
            .id(RefundServiceImplTest.GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)
            .email("mock1fullname@gmail.com")
            .forename("mock1-Forename")
            .surname("mock1-Surname")
            .roles(List.of("payments-refund", "payments-refund-approver", "caseworker-damage"))
            .active(true)
            .lastModified("2021-07-20T11:03:08.067Z")
            .build()};

    public static final Supplier<IdamUserInfoResponse[]> idamFullNameSendBackRefundListSupplier =
        () -> new IdamUserInfoResponse[]{IdamUserInfoResponse
            .idamFullNameRetrivalResponseWith()
            .id(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
            .email("mock2fullname@gmail.com")
            .forename("mock2-Forename")
            .surname("mock2-Surname")
            .roles(List.of("payments-refund", "payments-refund-approver", "refund-admin"))
            .active(true)
            .lastModified("2021-07-20T11:03:08.067Z")
            .build()};
    public static final Supplier<IdamUserIdResponse> idamUserIDResponseSupplier = () -> IdamUserIdResponse.idamUserIdResponseWith()
        .familyName("mock-Surname")
        .givenName("mock-ForeName")
        .name("mock-ForeName mock-Surname")
        .sub("mockfullname@gmail.com")
        .roles(List.of("payments-refund", "payments-refund-approver", "refund-admin"))
        .uid(GET_REFUND_LIST_CCD_CASE_USER_ID1)
        .build();
    private static final String REFUND_REFERENCE_REGEX = "^[RF-]{3}(\\w{4}-){3}(\\w{4})";
    private RefundReason refundReason = RefundReason.refundReasonWith()
        .code("RR031")
        .description("No comments")
        .name("Other - divorce")
        .build();
    private IdamUserIdResponse mockIdamUserIdResponse = IdamUserIdResponse.idamUserIdResponseWith()
        .familyName("VP")
        .givenName("VP")
        .name("VP")
        .sub("V_P@gmail.com")
        .roles(Collections.singletonList("vp"))
        .uid("986-erfg-kjhg-123")
        .build();
    private Refund refund = Refund.refundsWith()
        .amount(new BigDecimal(100))
        .paymentReference("RC-1111-2222-3333-4444")
        .reason("test-123")
        .feeIds("1")
        .refundStatus(uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL)
        .reference("RF-1234-1234-1234-1234")
        .serviceType("cmc")
        .build();
    private ObjectMapper mapper = new ObjectMapper();
    private RefundRequest refundRequest = RefundRequest.refundRequestWith()
        .paymentReference("RC-1234-1234-1234-1234")
        .refundAmount(new BigDecimal(100))
        .refundReason("RR002")
        .serviceType("cmc")
        .ccdCaseNumber("1111222233334444")
        .feeIds("1")
        .paymentMethod("cash")
        .paymentChannel("bulk scan")
        .contactDetails(ContactDetails.contactDetailsWith()
                .email("abc@abc.com")
                .notificationType("EMAIL")
                .build())
        .build();
    private RefundRequest refundForRetroRequest = RefundRequest.refundRequestWith()
        .paymentReference("RC-1234-1234-1234-1234")
        .refundAmount(new BigDecimal(100))
        .refundReason("RR036")
        .ccdCaseNumber("1111222233334444")
        .feeIds("1")
        .serviceType("cmc")
        .paymentMethod("cash")
        .contactDetails(ContactDetails.contactDetailsWith()
                .addressLine("ABC Street")
                .city("London")
                .county("Greater London")
                .country("UK")
                .postalCode("E1 6AN")
                .notificationType("LETTER")
                .build())
        .build();

    private List<Refund> getRefundList() {
        List<Refund> refunds = new ArrayList<>();
        Refund ref =  Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.APPROVED)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .build();

        refunds.add(ref);
        return refunds;
    }

    private List<PaymentDto> getPayments() {

        List<PaymentDto> payments = new ArrayList<>();

        PaymentDto payments1 = PaymentDto.payment2DtoWith()
            .accountNumber("123")
            .amount(BigDecimal.valueOf(100.00))
            .caseReference("test")
            .ccdCaseNumber("1111221383640739")
            .channel("bulk scan")
            .customerReference("123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .externalReference("test123")
            .giroSlipNo("tst")
            .method("cheque")
            .id("1")
            .paymentReference("RC-1111-2234-1077-1123")
            .serviceName("Service")
            .fees(Arrays.asList(FeeDto.feeDtoWith()
                                    .code("1")
                                    .jurisdiction1("test1")
                                    .jurisdiction2("test2")
                                    .version("1")
                                    .build()
                  )
            ).build();

        payments.add(payments1);
        return payments;
    }

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

    @MockBean
    private ContextStartListener contextStartListener;

    @Mock
    private IdamServiceImpl idamService;
    @InjectMocks
    private RefundsController refundsController;
    @Mock
    private ReferenceUtil referenceUtil;
    @MockBean
    private RefundReasonRepository refundReasonRepository;
    @MockBean
    @Qualifier("restTemplateIdam")
    private RestTemplate restTemplateIdam;

    @MockBean
    @Qualifier("restTemplateLiberata")
    private RestTemplate restTemplateLiberata;

    @Value("${idam.api.url}")
    private String idamBaseUrl;
    @Mock
    private MultiValueMap<String, String> map;

    @MockBean
    @Qualifier("restTemplatePayment")
    private RestTemplate restTemplatePayment;

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

    @MockBean
    private RefundNotificationService refundNotificationService;

    @MockBean
    private Specification<Refund> mockSpecification;

    @Mock
    private PaymentService paymentService;

    @MockBean
    @Qualifier("restTemplateNotify")
    private RestTemplate restTemplateNotify;


    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final DateTimeFormatter INVALID_FORMAT = DateTimeFormat.forPattern("yyyy-MM");

    @Mock
    private NotificationServiceImpl notificationService;

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
    void givenBlankCcdCaseNumberAndStatus_whenGetRefundList_thenRefundListEmptyExceptionIsReceived() {
        Exception exception = assertThrows(
            RefundListEmptyException.class,
            () -> refundsController.getRefundList(null, null, "", "", null)
        );
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(
            "Please provide criteria to fetch refunds i.e. Refund status or ccd case number"));
    }

    @Test
    void givenCcdCaseNumber_whenGetRefundList_thenRefundListIsReceived() throws Exception {

        mockUserinfoCall(idamUserIDResponseSupplier.get());

        mockGetUsersForRolesCall(
            Arrays.asList("refund-approver", "refund-admin"),
            idamFullNameCCDSearchRefundListSupplier.get()
        );

        //mock repository call
        when(refundsRepository.findByCcdCaseNumber(GET_REFUND_LIST_CCD_CASE_USER_ID1))
            .thenReturn(Optional.ofNullable(List.of(
                RefundServiceImplTest.refundListSupplierBasedOnCCDCaseNumber1.get())));

        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        userMap.put(
            "payments-refund",
                 Collections
                        .singletonList(UserIdentityDataDto.userIdentityDataWith().id(GET_REFUND_LIST_CCD_CASE_USER_ID1)
                                .fullName("mock-Forename mock-Surname").emailId("mockfullname@gmail.com").build())
        );
        when(contextStartListener.getUserMap()).thenReturn(userMap);
        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().name(
            "refund reason").build()));

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(RefundReason.refundReasonWith()
                                                                                   .code("RR002")
                                                                                   .name("Amended court")
                                                                                   .build());
        when(refundReasonRepository.findAll()).thenReturn(
            Arrays.asList(RefundReason.refundReasonWith().code("RR001").name("Amended court").build()));
        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "submitted")
                                                  .queryParam("ccdCaseNumber", GET_REFUND_LIST_CCD_CASE_USER_ID1)
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
        assertEquals(
            uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL,
            refundListDtoResponse.getRefundList().get(0).getRefundStatus()
        );
    }

    @Test
    void testRefundListForSubmittedStatus() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockGetUsersForRolesCall(
            Arrays.asList("refund-approver", "refund-admin"),
            idamFullNameCCDSearchRefundListSupplier.get()
        );
        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        userMap.put(
            "payments-refund",
            Arrays.asList(UserIdentityDataDto.userIdentityDataWith().id(GET_REFUND_LIST_CCD_CASE_USER_ID1)
                              .fullName("mock-Forename mock-Surname").emailId("mockfullname@gmail.com").build())
        );
        when(contextStartListener.getUserMap()).thenReturn(userMap);


        //mock repository call
        when(refundsRepository.findByRefundStatus(
            uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL
        ))
            .thenReturn(Optional.ofNullable(List.of(
                RefundServiceImplTest.refundListSupplierBasedOnCCDCaseNumber1.get())));
        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith()
                                                                                        .code("RR002")
                                                                                        .name("Amended court")
                                                                                        .build()));
        when(refundReasonRepository.findAll()).thenReturn(
            Arrays.asList(RefundReason.refundReasonWith().code("RR001").name("Amended court").build()));

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "Sent for approval")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("excludeCurrentUser", "null")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();

        RefundListDtoResponse refundListDtoResponse = mapper.readValue(
            mvcResult.getResponse().getContentAsString(), RefundListDtoResponse.class
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(1, refundListDtoResponse.getRefundList().size());
        assertEquals("mock-Forename mock-Surname", refundListDtoResponse.getRefundList().get(0).getUserFullName());
        assertEquals(
            uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL,
            refundListDtoResponse.getRefundList().get(0).getRefundStatus()
        );
    }

    @Test
    void testInvalidInputException() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("excludeCurrentUser", "")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest()).andReturn();

        String errorMessage = mvcResult.getResponse().getContentAsString();
        assertTrue(errorMessage.equals("Please provide criteria to fetch refunds i.e. Refund status or ccd case number"));

    }

    @Test
    void testInvalidRefundReasonException() throws Exception {

        mockUserinfoCall(idamUserIDResponseSupplier.get());

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "Invalid status")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("excludeCurrentUser", "")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest()).andReturn();

        String errorMessage = mvcResult.getResponse().getContentAsString();
        assertTrue(errorMessage.equals("Invalid Refund status"));

    }

    @Test
    void testMultipleRefundsSubmittedStatus() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockGetUsersForRolesCall(
            Arrays.asList("payments-refund", "payments-refund-approver"),
            idamFullNameCCDSearchRefundListSupplier1.get()
        );

        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        userMap.put(
            "payments-refund",
            Arrays.asList(UserIdentityDataDto.userIdentityDataWith().id("1f2b7025-0f91-4737-92c6-b7a9baef14c6")
                              .fullName("mock-Forename mock-Surname").emailId("mockfullname@gmail.com").build())
        );
        when(contextStartListener.getUserMap()).thenReturn(userMap);

        //mock repository call
        when(refundsRepository.findByRefundStatus(
            uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL
        ))
            .thenReturn(Optional.ofNullable(List.of(
                RefundServiceImplTest.refundListSupplierBasedOnCCDCaseNumber1.get())));
        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith()
                                                                                        .code("RR002")
                                                                                        .name("Amended court")
                                                                                        .build()));
        when(refundReasonRepository.findAll()).thenReturn(
            Arrays.asList(RefundReason.refundReasonWith().code("RR001").name("Amended court").build()));
        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "Sent for approval")
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
        assertEquals(
            uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL,
            refundListDtoResponse.getRefundList().get(0).getRefundStatus()
        );

        assertTrue(refundListDtoResponse.getRefundList().stream()
                       .anyMatch(refundListDto -> refundListDto.getUserFullName().equalsIgnoreCase(
                           "mock-Forename mock-Surname")));
    }

    @Test
    void testRefundsListSendBackStatus() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockGetUsersForRolesCall(
            Arrays.asList("payments-refund", "payments-refund-approver"),
            idamFullNameSendBackRefundListSupplier.get()
        );

        //mock repository call
        when(refundsRepository.findByRefundStatus(
            RefundStatus.UPDATEREQUIRED
        ))
            .thenReturn(Optional.ofNullable(List.of(
                RefundServiceImplTest.refundListSupplierForSendBackStatus.get())));

        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        userMap.put(
            "payments-refund",
            Arrays.asList(UserIdentityDataDto.userIdentityDataWith().id(
                GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
                              .fullName("mock2-Forename mock2-Surname").emailId("mock2fullname@gmail.com").build())
        );
        when(contextStartListener.getUserMap()).thenReturn(userMap);

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "Update required")
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
        assertEquals(
            RefundStatus.UPDATEREQUIRED,
            refundListDtoResponse.getRefundList().get(0).getRefundStatus()
        );
        assertEquals("mock2-Forename mock2-Surname", refundListDtoResponse.getRefundList().get(0).getUserFullName());
    }

    public void mockUserinfoCall(IdamUserIdResponse idamUserIdResponse) {
        UriComponentsBuilder builderForUserInfo = UriComponentsBuilder.fromUriString(idamBaseUrl + IdamServiceImpl.USERID_ENDPOINT);
        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(idamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(
            eq(builderForUserInfo.toUriString()),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
    }

    public void mockGetUsersForRolesCall(List<String> roles, IdamUserInfoResponse[] idamUserListResponse) {
        String query = "(roles:payments-refund OR roles:payments-refund-approver OR roles:refund-admin) AND lastModified:>now-720d";
        int size = 300;
        UriComponents builder = UriComponentsBuilder.newInstance()
            .fromUriString(idamBaseUrl + IdamServiceImpl.USER_FULL_NAME_ENDPOINT)
            .query("query={query}")
            .query("size={size}")
            .buildAndExpand(query, size);
        ResponseEntity<IdamUserInfoResponse[]> responseEntity =
            new ResponseEntity<>(idamUserListResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(
            eq(builder.toUriString()),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(IdamUserInfoResponse[].class)
        )).thenReturn(responseEntity);
    }

    @Test
    void getRefundReasonsList() throws Exception {
        when(refundReasonRepository.findAll()).thenReturn(
            Arrays.asList(RefundReason.refundReasonWith().code("RR001").name("Amended claim").build()));

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
        assertEquals(1, refundReasonList.size());
    }

    @Test
    void createRefund() throws Exception {

        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(Collections.emptyList()));

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(RefundReason.refundReasonWith()
                                                                                   .code("RR035")
                                                                                   .name("Other - Claim")
                                                                                   .build());
        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        MvcResult result = mockMvc.perform(post("/refund")
                                               .content(asJsonString(RefundRequest.refundRequestWith()
                                                                         .paymentReference("RC-1234-1234-1234-1234")
                                                                         .refundAmount(new BigDecimal(100))
                                                                         .refundReason("RR035-Other-Reason")
                                                                         .ccdCaseNumber("1111222233334444")
                                                                         .feeIds("1")
                                                                         .serviceType("cmc")
                                                                         .paymentMethod("cash")
                                                                         .paymentChannel("bulk scan")
                                                                         .paymentMethod("cash")
                                                                         .contactDetails(ContactDetails.contactDetailsWith()
                                                                                 .email("abc@abc.com")
                                                                                 .notificationType("EMAIL")
                                                                                 .build())
                                                                         .build()))

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
    void createRefundForRetroRemission() throws Exception {

        when(refundsRepository.findByPaymentReference(anyString())).thenReturn(Optional.of(Collections.emptyList()));

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(RefundReason.refundReasonWith()
                                                                                   .code("RR036")
                                                                                   .name("Retrospective remission")
                                                                                   .build());

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        MvcResult result = mockMvc.perform(post("/refund")
                                               .content(asJsonString(refundForRetroRequest))
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
            .paymentMethod("card")
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
    @Disabled("Disabled this unit test as the respective validation is disabled to allow partial refunds")
    void createRefundReturns400ForAlreadyRefundedPaymentReference() throws Exception {

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);

        Refund refund = Refund.refundsWith()
            .amount(refundRequest.getRefundAmount())
            .paymentReference(refundRequest.getPaymentReference())
            .reason(refundReasonRepository.findByCodeOrThrow(refundRequest.getRefundReason()).getCode())
            .refundStatus(uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL)
            .reference(referenceUtil.getNext("RF"))
            .feeIds("1")
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

        String errorMessage = result.getResponse().getContentAsString();
        assertTrue(errorMessage.equals("Refund is already requested for this payment"));
    }

    @Test
    void createRefundReturns504ForGatewayTimeout() throws Exception {

        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(RefundReason.refundReasonWith()
                                                                                   .code("RR035")
                                                                                   .name("Other - Reason")
                                                                                   .build());

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenThrow(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT));

        MvcResult result = mockMvc.perform(post("/refund")
                                               .content(asJsonString(RefundRequest.refundRequestWith()
                                                                         .paymentReference("RC-1234-1234-1234-1234")
                                                                         .refundAmount(new BigDecimal(100))
                                                                         .refundReason("RR035-Other-Reason")
                                                                         .ccdCaseNumber("1111222233334444")
                                                                         .feeIds("1")
                                                                         .serviceType("cmc")
                                                                         .paymentMethod("card")
                                                                         .contactDetails(ContactDetails.contactDetailsWith().build())
                                                                         .build()))
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
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenReturn(
             new ResponseEntity<String>("Success", HttpStatus.OK)
        );
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(eq("refund-liberata"), anyBoolean())).thenReturn(true);
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
        ReconciliationProviderResponse reconciliationProviderResponse = ReconciliationProviderResponse.buildReconciliationProviderResponseWith()
                .amount(BigDecimal.valueOf(100))
                .refundReference("RF-1628-5241-9956-2215")
                .build();
        when(restTemplateLiberata.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(ReconciliationProviderResponse.class)))
                .thenReturn(ResponseEntity.of(Optional.of(reconciliationProviderResponse)));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());

        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().name(
            "refund reason").build()));

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
    void approveRefundRequestReturnsSuccessResponsewithLetternotification() throws Exception {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenReturn(
             new ResponseEntity<String>("Success", HttpStatus.OK)
        );
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(eq("refund-liberata"), anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefundWithLetterDetails()));

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
        ))).when(restTemplateLiberata).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefundWithLetterDetails());

        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().name(
            "refund reason").build()));

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
    void anyRefundReviewActionOnUnSubmittedRefundReturnsBadRequest() throws Exception {
        Refund unsubmittedRefund = getRefund();
        unsubmittedRefund.setRefundStatus(RefundStatus.UPDATEREQUIRED);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(unsubmittedRefund));
        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");

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
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR001", "reason1");
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));
        when(authTokenGenerator.generate()).thenReturn("service auth token");

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
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
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
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
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(getRefund()));

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);


        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
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
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
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
        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().code(
            "RR002").name("refund reason").build()));


        when(restTemplateLiberata.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
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
        assertEquals("Invalid request. Please try again.", result.getResponse().getContentAsString());
    }

    @Test
    void approveRefundRequestWhenReconciliationProviderIsUnavailableReturnsInternalServerError() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
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

        when(restTemplateLiberata.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(ReconciliationProviderResponse.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().code(
            "RR002").name("refund reason").build()));


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
        assertEquals(
            "Third-party reconciliation provider unavailable. Please try again later.",
            result.getResponse().getContentAsString()
        );
    }

    @Test
    void approveRefundRequestWhenReconciliationProviderReceivesDuplicateRequestReturnsDuplicateRequestMessage() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
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

        when(restTemplateLiberata.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(ReconciliationProviderResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));


        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().code(
            "RR002").name("refund reason").build()));


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
        assertEquals(
            "Duplicate request.",
            result.getResponse().getContentAsString()
        );
    }


    @Test
    void approveRefundRequest_WhenRefundIsNotAvailable() throws Exception {
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

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

        Refund refundWithRetroRemission = getRefund();
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenReturn(
            new ResponseEntity<String>("Success", HttpStatus.OK)
        );
        refundWithRetroRemission.setReason("RR036");
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
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
        ReconciliationProviderResponse reconciliationProviderResponse = ReconciliationProviderResponse.buildReconciliationProviderResponseWith()
                .amount(BigDecimal.valueOf(100))
                .refundReference("RF-1628-5241-9956-2215")
                .build();
        when(restTemplateLiberata.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                eq(ReconciliationProviderResponse.class)))
                .thenReturn(ResponseEntity.of(Optional.of(reconciliationProviderResponse)));

        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());
        when(refundReasonRepository.findByCode(anyString())).thenReturn(
            Optional.of(RefundReason.refundReasonWith().code("RR036").name("Retrospective Remission").build()));
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenReturn(
            new ResponseEntity<String>("Success", HttpStatus.OK)
        );

        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");

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

        Refund refundWithRetroRemission = getRefund();
        refundWithRetroRemission.setReason("RR036");
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
        when(refundsRepository.findByReference(anyString())).thenReturn(Optional.of(refundWithRetroRemission));


        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        PaymentGroupResponse paymentGroupResponse = getPaymentGroupDto();
        paymentGroupResponse.setRemissions(Arrays.asList());
        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().name(
            "refund reason").build()));

        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(paymentGroupResponse)

        ));
        doReturn(ResponseEntity.ok(Optional.of(ReconciliationProviderResponse.buildReconciliationProviderResponseWith()
                                                   .amount(BigDecimal.valueOf(100))
                                                   .refundReference("RF-1628-5241-9956-2215")
                                                   .build()
        ))).when(restTemplateLiberata).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());

        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");

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

        Refund refundWithRetroRemission = getRefund();
        refundWithRetroRemission.setReason("RR036");
        refundWithRetroRemission.setAmount(BigDecimal.valueOf(10));
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
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
        ))).when(restTemplateLiberata).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());
        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().name(
            "refund reason").build()));
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");

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
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class),
                                         eq(String.class))).thenReturn(new ResponseEntity<String>("Success", HttpStatus.OK)
        );
        Refund refundWithRetroRemission = getRefund();
        refundWithRetroRemission.setReason("RR036");
        when(featureToggler.getBooleanValue(eq("refunds-release"),anyBoolean())).thenReturn(false);
        when(featureToggler.getBooleanValue(eq("refund-liberata"),anyBoolean())).thenReturn(true);
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
        doReturn(new ResponseEntity(HttpStatus.PERMANENT_REDIRECT)).when(restTemplateLiberata).exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(
                ReconciliationProviderResponse.class)
        );
        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().name(
            "refund reason").build()));
        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
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
        assertEquals(
            "Reconciliation provider unavailable. Please try again later.",
            result.getResponse().getContentAsString()
        );
    }

    @Test
    void retrieveActionsForSubmittedState() throws Exception {
        refund.setRefundStatus(uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL);
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
        refund.setRefundStatus(RefundStatus.UPDATEREQUIRED);
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
        refund.setRefundStatus(uk.gov.hmcts.reform.refunds.model.RefundStatus.ACCEPTED);
        when(refundsRepository.findByReferenceOrThrow(any())).thenReturn(refund);
        mockMvc.perform(get("/refund/RF-1234-1234-1234-1231/actions")
                            .header("Authorization", "user")
                            .header("ServiceAuthorization", "service")
                            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$").value("Action not allowed to proceed"));
    }

    @Test
    void retrieveActionsForApprovedState() throws Exception {
        refund.setRefundStatus(RefundStatus.APPROVED);
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
    void updateRefundStatusAccepted() throws Exception {

        refund.setRefundStatus(RefundStatus.APPROVED);
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        RefundStatusUpdateRequest refundStatusUpdateRequest = RefundStatusUpdateRequest.RefundRequestWith().status(
            uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED).build();
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
    void updateRefundStatusRejected() throws Exception {

        refund.setRefundStatus(RefundStatus.APPROVED);
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        RefundStatusUpdateRequest refundStatusUpdateRequest = new RefundStatusUpdateRequest(
            "Refund rejected",
            uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED
        );
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
    void updateRefundStatusRejectedWithOutReason() throws Exception {
        RefundStatusUpdateRequest refundStatusUpdateRequest = RefundStatusUpdateRequest.RefundRequestWith()
            .status(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED).build();
        refund.setRefundStatus(RefundStatus.APPROVED);

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
    void updateRefundStatusNotAllowedWithCurrentStatus() throws Exception {

        refund.setRefundStatus(uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL);
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);

        IdamUserIdResponse mockIdamUserIdResponse = getIdamResponse();

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);
        RefundStatusUpdateRequest refundStatusUpdateRequest = new RefundStatusUpdateRequest(
            "Refund rejected",
            uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED
        );
        MvcResult result = mockMvc.perform(patch(
            "/refund/{reference}",
            "RF-1234-1234-1234-1234"
        )
                                               .content(asJsonString(refundStatusUpdateRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
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

        StatusHistoryResponseDto statusHistoryResponseDto = StatusHistoryResponseDto.statusHistoryResponseDtoWith()
            .lastUpdatedByCurrentUser(false)
            .statusHistoryDtoList(Collections.singletonList(statusHistoryDto))
            .build();

        when(refundsService.getStatusHistory(any(), anyString())).thenReturn(statusHistoryResponseDto);

        // when
        ResponseEntity<StatusHistoryResponseDto> result = refundsController.getStatusHistory(null, null, "reference");

        // then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(200, result.getStatusCodeValue());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().getStatusHistoryDtoList().size());
        assertEquals(statusHistoryDto, result.getBody().getStatusHistoryDtoList().get(0));
        assertEquals(1, result.getBody().getStatusHistoryDtoList().get(0).getId());
        assertEquals(1, result.getBody().getStatusHistoryDtoList().get(0).getRefundsId());
        assertEquals("AAA", result.getBody().getStatusHistoryDtoList().get(0).getStatus());
        assertEquals("BBB", result.getBody().getStatusHistoryDtoList().get(0).getNotes());
        assertEquals(
            Timestamp.valueOf("2021-10-10 10:10:10"),
            result.getBody().getStatusHistoryDtoList().get(0).getDateCreated()
        );
        assertEquals("CCC", result.getBody().getStatusHistoryDtoList().get(0).getCreatedBy());

    }

    @Test
    void testResubmitRefund() {

        ResubmitRefundRequest
            resubmitRefundRequest =
            ResubmitRefundRequest.ResubmitRefundRequestWith().refundReason("WWW").amount(BigDecimal.valueOf(333))
                .build();
        ResubmitRefundResponseDto resubmitRefundResponseDto =
            ResubmitRefundResponseDto.buildResubmitRefundResponseDtoWith()
                .refundReference("RF-1111-1111-1111-1111")
                .refundAmount(resubmitRefundRequest.getAmount()).build();

        when(refundsService.resubmitRefund(anyString(), any(), any()))
            .thenReturn(resubmitRefundResponseDto);

        ResponseEntity<ResubmitRefundResponseDto> responseEntity =
            refundsController.resubmitRefund(null, null, "RF-1111-1111-1111-1111", resubmitRefundRequest);
        verify(refundsService, times(1)).resubmitRefund("RF-1111-1111-1111-1111", resubmitRefundRequest, null);
        assertNotNull(responseEntity.getBody());
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertEquals(BigDecimal.valueOf(333), responseEntity.getBody().getRefundAmount());
        assertEquals("RF-1111-1111-1111-1111", responseEntity.getBody().getRefundReference());
    }

    @Test
    void givenNullAmount_whenResubmitRefund_thenBadRequestStatusIsReceived() throws Exception {

        refund.setRefundStatus(RefundStatus.APPROVED);
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(RefundServiceImplTest.refundListSupplierForSendBackStatus.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(RefundReason.refundReasonWith()
                                                                                   .code("RR002")
                                                                                   .name("Amended court")
                                                                                   .build());
        ResubmitRefundRequest resubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
            .refundReason("RR003").build();

        MvcResult result = mockMvc.perform(patch(
            "/refund/resubmit/{reference}",
            "RF-1234-1234-1234-1234"
        )
                                               .content(asJsonString(resubmitRefundRequest))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
    }

    @Test
    void givenNotificationRequest_ResendNotificationShouldReturnSuccessStatus() throws Exception {
        when(refundNotificationService.resendRefundNotification(any(ResendNotificationRequest.class),any())).thenReturn(
            new ResponseEntity<>(HttpStatus.OK)
        );
        MvcResult result = mockMvc.perform(put(
            "/resend/notification/{reference}",
            "RF-1234-1234-1234-1234"
        ).param("notificationType","EMAIL")
                                   .content(asJsonString(getMockEmailRequest()))
                                   .header("Authorization", "user")
                                   .header("ServiceAuthorization", "Services")
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .accept(MediaType.APPLICATION_JSON))
                                   .andExpect(status().isOk())
                                    .andReturn();
    }

    @Test
    void givenNotificationRequestWithoutNotificationTypeParam_ResendNotificationShouldReturnBadRequestStatus() throws Exception {
        MvcResult result = mockMvc.perform(put(
            "/resend/notification/{reference}",
            "RF-1234-1234-1234-1234"
        )
                                               .content(asJsonString(getMockEmailRequest()))
                                               .header("Authorization", "user")
                                               .header("ServiceAuthorization", "Services")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();
    }


    private ResendNotificationRequest getMockEmailRequest() {
        return ResendNotificationRequest.resendNotificationRequest()
            .recipientEmailAddress("mock@gmail.com")
            .notificationType(NotificationType.EMAIL)
            .reference("RF-1233-2134-1234-1234")
            .build();
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
            .refundStatus(uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL)
            .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .updatedBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .feeIds("50")
            .contactDetails(ContactDetails.contactDetailsWith()
                                .email("abc@abc.com")
                                .notificationType("EMAIL")
                                .build())
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                                               .id(1)
                                               .status(RefundStatus.SENTFORAPPROVAL.getName())
                                               .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                                               .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                                               .notes("Refund initiated and sent to team leader")
                                               .build()))
            .build();
    }

    private Refund getRefundWithLetterDetails() {
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
            .contactDetails(ContactDetails.contactDetailsWith()
                                .addressLine("ABC Street")
                                .city("London")
                                .county("Greater London")
                                .country("UK")
                                .postalCode("E1 6AN")
                                .notificationType("LETTER")
                                .build())
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                                               .id(1)
                                               .status(RefundStatus.SENTFORAPPROVAL.getName())
                                               .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                                               .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                                               .notes("Refund initiated and sent to team leader")
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

    @Test
    void approveRefundRequestReturnsNotificationExceptionResponse() throws Exception {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenThrow(
            new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        RefundReviewRequest refundReviewRequest = new RefundReviewRequest("RR0001", "reason1");
        when(featureToggler.getBooleanValue(eq("refund-liberata"), anyBoolean())).thenReturn(true);
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
        ))).when(restTemplateLiberata).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());

        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().name(
            "refund reason").build()));
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

        //assertEquals("Refund approved", result.getResponse().getContentAsString());
    }

    @Test
     void returnException413_withValidBetweenDates_ExceedSearchCriteria() throws Exception {

        String startDate = LocalDate.now().minusDays(10).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult mvcResult = mockMvc.perform(get("/refunds/" + startDate + "/" + endDate)
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isPayloadTooLarge()).andReturn();
        Assertions.assertEquals("Date range exceeds the maximum supported by the system", mvcResult.getResolvedException().getMessage());

    }

    @Test
     void returnException400_withInvaliddBetweenDates_WhenStartDateIsBigger() throws Exception {

        String startDate = LocalDate.now().toString(DATE_FORMAT);
        String endDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);

        MvcResult mvcResult = mockMvc.perform(get("/refunds/" + startDate + "/" + endDate)
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError()).andReturn();
        Assertions.assertEquals("Start date cannot be greater than end date", mvcResult.getResolvedException().getMessage());

    }

    @Test
     void returnException400_withInvaliddBetweenDates_WhenStartDateIsInFuture() throws Exception {

        String startDate = LocalDate.now().plusDays(2).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult mvcResult = mockMvc.perform(get("/refunds/" + startDate + "/" + endDate)
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError()).andReturn();
        Assertions.assertEquals("Date cannot be in the future", mvcResult.getResolvedException().getMessage());

    }

    @Test
      void returnException400_withInvaliddBetweenDates_WhenInvalidFormat() throws Exception {

        String startDate = LocalDate.now().minusDays(1).toString(INVALID_FORMAT);
        String endDate = LocalDate.now().toString(INVALID_FORMAT);

        MvcResult mvcResult = mockMvc.perform(get("/refunds/" + startDate + "/" + endDate)
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError()).andReturn();
        Assertions.assertEquals("Invalid date format received, required data format is ISO", mvcResult.getResolvedException().getMessage());

    }

    @Test
     void validateSuccessResponseWhenValidSearchDateProvided() throws Exception {

        RefundsServiceImpl mock = org.mockito.Mockito.mock(RefundsServiceImpl.class);
        when(mock.searchByCriteria(getSearchCriteria())).thenReturn(mockSpecification);

        when(refundsRepository.findAll(any()))
            .thenReturn(getRefundList());
        List<String> referenceList = new ArrayList<>();
        referenceList.add("RC-1111-2234-1077-1123");
        when(paymentService.fetchPaymentResponse(referenceList)).thenReturn(getPayments());

        ResponseEntity<List<PaymentDto>> responseEntity = new ResponseEntity<>(getPayments(), HttpStatus.OK);

        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                   eq(new ParameterizedTypeReference<List<PaymentDto>>() {
                                   }))).thenReturn(responseEntity);

        String startDate = LocalDate.now().minusDays(1).toString(DATE_FORMAT);
        String endDate = LocalDate.now().toString(DATE_FORMAT);

        MvcResult mvcResult = mockMvc.perform(get("/refunds/" + startDate + "/" + endDate)
                                                  .header("ServiceAuthorization", "Services")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();

        RerfundLiberataResponse rerfundLiberataResponse = mapper.readValue(
            mvcResult.getResponse().getContentAsString(), RerfundLiberataResponse.class
        );

        Assertions.assertEquals("RF-1111-2234-1077-1123", rerfundLiberataResponse.getRefunds().get(0).getReference());

    }


    @Test
      void testRefundSearchCriteria() throws Exception {

        DateUtil date = new DateUtil();
        Date fromDate = date.getIsoDateTimeFormatter().parseDateTime("2021-11-02")
            .toDate();

        Date toDate = date.getIsoDateTimeFormatter().parseDateTime("2021-11-03")
            .toDate();

        Assertions.assertEquals(fromDate,getSearchCriteria().getStartDate());
        Assertions.assertEquals(toDate,getSearchCriteria().getEndDate());
        Assertions.assertEquals("RF-1111-2234-1077-1123",getSearchCriteria().getRefundReference());

    }

    private RefundSearchCriteria getSearchCriteria() {
        DateUtil date = new DateUtil();
        Date fromDate = date.getIsoDateTimeFormatter().parseDateTime("2021-11-02")
            .toDate();

        Date toDate = date.getIsoDateTimeFormatter().parseDateTime("2021-11-03")
            .toDate();
        return RefundSearchCriteria.searchCriteriaWith()
            .startDate(fromDate)
            .endDate(toDate)
            .refundReference("RF-1111-2234-1077-1123")
            .build();
    }

    @Test
    void processFailedLiberataRefundsApproveJourneyTest() throws Exception {
        MvcResult result = mockMvc.perform(patch("/jobs/refund-approved-update").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    }

    @Test
    void processFailedNotifcationsTest() throws Exception {
        MvcResult result = mockMvc.perform(patch("/jobs/refund-notification-update").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    }
}
