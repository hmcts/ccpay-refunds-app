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
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamFullNameRetrivalResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.IdamServiceImpl;
import uk.gov.hmcts.reform.refunds.services.RefundsServiceImpl;
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTBACK;
import static uk.gov.hmcts.reform.refunds.model.RefundStatus.SUBMITTED;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.*;
import static uk.gov.hmcts.reform.refunds.services.IdamServiceImpl.USERID_ENDPOINT;
import static uk.gov.hmcts.reform.refunds.services.IdamServiceImpl.USER_FULL_NAME_ENDPOINT;


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

    public static final Supplier<IdamFullNameRetrivalResponse> idamFullNameCCDSearchRefundListSupplier = () -> IdamFullNameRetrivalResponse
        .idamFullNameRetrivalResponseWith()
        .id(GET_REFUND_LIST_CCD_CASE_USER_ID)
        .email("mockfullname@gmail.com")
        .forename("mock-Forename")
        .surname("mock-Surname")
        .roles(List.of("Refund-approver", "Refund-admin"))
        .build();

    public static final Supplier<IdamFullNameRetrivalResponse> idamFullNameSubmittedRefundListSupplier = () -> IdamFullNameRetrivalResponse
        .idamFullNameRetrivalResponseWith()
        .id(GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)
        .email("mock1fullname@gmail.com")
        .forename("mock1-Forename")
        .surname("mock1-Surname")
        .roles(List.of("Refund-approver", "Refund-admin"))
        .build();

    public static final Supplier<IdamFullNameRetrivalResponse> idamFullNameSendBackRefundListSupplier = () -> IdamFullNameRetrivalResponse
        .idamFullNameRetrivalResponseWith()
        .id(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
        .email("mock2fullname@gmail.com")
        .forename("mock2-Forename")
        .surname("mock2-Surname")
        .roles(List.of("Refund-approver", "Refund-admin"))
        .build();

    public static final Supplier<IdamUserIdResponse> idamUserIDResponseSupplier = () -> IdamUserIdResponse.idamUserIdResponseWith()
        .familyName("mock-Surname")
        .givenName("mock-Surname")
        .name("mock-ForeName")
        .sub("mockfullname@gmail.com")
        .roles(List.of("Refund-approver", "Refund-admin"))
        .uid(GET_REFUND_LIST_CCD_CASE_USER_ID)
        .build();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Mock
    private RefundsServiceImpl refundsService;
    @MockBean
    private RefundsRepository refundsRepository;
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
                                                  .queryParam("selfExclusive", " ")
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
        assertEquals(SUBMITTED, refundListDtoResponse.getRefundList().get(0).getRefundStatus());
    }

    @Test
    public void testRefundListForSubmittedStatus() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockIdamFullNameCall(GET_REFUND_LIST_CCD_CASE_USER_ID, idamFullNameCCDSearchRefundListSupplier.get());

        //mock repository call
        when(refundsRepository.findByRefundStatusAndCreatedByIsNot(
            RefundStatus.SUBMITTED,
            GET_REFUND_LIST_CCD_CASE_USER_ID
        ))
            .thenReturn(Optional.ofNullable(List.of(
                refundListSupplierBasedOnCCDCaseNumber.get())));

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "submitted")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("selfExclusive", " ")
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
        assertEquals(SUBMITTED, refundListDtoResponse.getRefundList().get(0).getRefundStatus());
    }

    @Test
    public void testMultipleRefundsSubmittedStatus() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockIdamFullNameCall(GET_REFUND_LIST_CCD_CASE_USER_ID, idamFullNameCCDSearchRefundListSupplier.get());
        mockIdamFullNameCall(GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID, idamFullNameSubmittedRefundListSupplier.get());

        //mock repository call
        when(refundsRepository.findByRefundStatus(
            RefundStatus.SUBMITTED
        ))
            .thenReturn(Optional.ofNullable(List.of(
                refundListSupplierBasedOnCCDCaseNumber.get(), refundListSupplierForSubmittedStatus.get())));

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "submitted")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("selfExclusive", "false")
                                                  .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();

        RefundListDtoResponse refundListDtoResponse = mapper.readValue(
            mvcResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(2, refundListDtoResponse.getRefundList().size());
        assertEquals(SUBMITTED, refundListDtoResponse.getRefundList().get(0).getRefundStatus());

        assertTrue(refundListDtoResponse.getRefundList().stream()
                       .anyMatch(refundListDto -> refundListDto.getUserFullName().equalsIgnoreCase("mock-Forename mock-Surname")));

        assertTrue(refundListDtoResponse.getRefundList().stream()
                       .anyMatch(refundListDto -> refundListDto.getUserFullName().equalsIgnoreCase(
                           "mock1-Forename mock1-Surname")));
    }

    @Test
    public void testRefundsListSendBackStatus() throws Exception {

        //mock userinfo call
        mockUserinfoCall(idamUserIDResponseSupplier.get());

        //mock idam userFullName call
        mockIdamFullNameCall(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID, idamFullNameSendBackRefundListSupplier.get());

        //mock repository call
        when(refundsRepository.findByRefundStatus(
            RefundStatus.SENTBACK
        ))
            .thenReturn(Optional.ofNullable(List.of(
                refundListSupplierForSendBackStatus.get())));

        MvcResult mvcResult = mockMvc.perform(get("/refund")
                                                  .header("Authorization", "user")
                                                  .header("ServiceAuthorization", "Services")
                                                  .queryParam("status", "sentback")
                                                  .queryParam("ccdCaseNumber", "")
                                                  .queryParam("selfExclusive", "false")
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
                                     IdamFullNameRetrivalResponse idamFullNameRetrivalResponse) {
        UriComponentsBuilder builderCCDSearchURI = UriComponentsBuilder.fromUriString(idamBaseURL + USER_FULL_NAME_ENDPOINT)
            .queryParam("query", "id:" + userId);
        ResponseEntity<IdamFullNameRetrivalResponse> responseForFullNameCCDUserId =
            new ResponseEntity<>(idamFullNameRetrivalResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(eq(builderCCDSearchURI.toUriString())
            , any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamFullNameRetrivalResponse.class)
        )).thenReturn(responseForFullNameCCDUserId);
    }

    public void mockUserinfoCall(IdamUserIdResponse idamUserIdResponse) {
        UriComponentsBuilder builderForUserInfo = UriComponentsBuilder.fromUriString(idamBaseURL + USERID_ENDPOINT);
        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(idamUserIdResponse, HttpStatus.OK);
        when(restTemplateIdam.exchange(eq(builderForUserInfo.toUriString()), any(HttpMethod.class), any(HttpEntity.class),
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
}
