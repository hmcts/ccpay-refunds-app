package uk.gov.hmcts.reform.refunds.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.*;
import uk.gov.hmcts.reform.refunds.exceptions.*;
import uk.gov.hmcts.reform.refunds.mapper.RefundResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.StatusHistoryResponseMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.services.IdamService;
import uk.gov.hmcts.reform.refunds.services.PaymentService;
import uk.gov.hmcts.reform.refunds.services.RefundsServiceImpl;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
public class RefundServiceImplTest {

    @InjectMocks
    private RefundsServiceImpl refundsService;

    @Mock
    private IdamService idamService;

    @Mock
    private MultiValueMap<String, String> map;

    @Mock
    private RefundsRepository refundsRepository;

    @Mock
    private StatusHistoryRepository statusHistoryRepository;

    @Mock
    private RefundReasonRepository refundReasonRepository;

    @Mock
    private PaymentService paymentService;

    @Spy
    private StatusHistoryResponseMapper statusHistoryResponseMapper;

    @Spy
    private RefundResponseMapper refundResponseMapper;

    public static final String GET_REFUND_LIST_CCD_CASE_NUMBER = "1111-2222-3333-4444";
    public static final String GET_REFUND_LIST_CCD_CASE_USER_ID1 = "1f2b7025-0f91-4737-92c6-b7a9baef14c6";
    public static final String GET_REFUND_LIST_CCD_CASE_USER_ID2 = "userId2";
    public static final String GET_REFUND_LIST_CCD_CASE_USER_ID3 = "userId3";

    public static final String GET_REFUND_LIST_SUBMITTED_REFUND_STATUS = "2222-2222-3333-4444";
    public static final String GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID = "2f2b7025-0f91-4737-92c6-b7a9baef14c6";

    public static final String GET_REFUND_LIST_SENDBACK_REFUND_STATUS = "3333-3333-3333-4444";
    public static final String GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID = "3f2b7025-0f91-4737-92c6-b7a9baef14c6";

    public static final Supplier<StatusHistory> STATUS_HISTORY_SUPPLIER = () -> StatusHistory.statusHistoryWith()
            .id(1)
            .status(RefundStatus.SENTBACK.getName())
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .build();

    public static final Supplier<Refund> refundListSupplierBasedOnCCDCaseNumber1 = () -> Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_NUMBER)
            .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .build();

    public static final Supplier<Refund> refundListSupplierBasedOnCCDCaseNumber2 = () -> Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_NUMBER)
            .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID2)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID2)
            .build();

    public static final Supplier<Refund> refundListSupplierBasedOnCCDCaseNumber3 = () -> Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_NUMBER)
            .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID3)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID3)
            .build();

    public static final Supplier<Refund> refundListSupplierForSubmittedStatus = () -> Refund.refundsWith()
            .id(2)
            .amount(BigDecimal.valueOf(200))
            .ccdCaseNumber(GET_REFUND_LIST_SUBMITTED_REFUND_STATUS)
            .createdBy(GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)
            .updatedBy(GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)
            .reference("RF-2222-2234-1077-1123")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .reason("Other")
            .paymentReference("RC-2222-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .build();

    public static final Supplier<Refund> refundListSupplierForSendBackStatus = () -> Refund.refundsWith()
            .id(3)
            .amount(BigDecimal.valueOf(300))
            .ccdCaseNumber(GET_REFUND_LIST_SENDBACK_REFUND_STATUS)
            .createdBy(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
            .updatedBy(GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
            .reference("RF-3333-2234-1077-1123")
            .refundStatus(RefundStatus.SENTBACK)
            .reason("Other")
            .paymentReference("RC-3333-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .statusHistories(Arrays.asList(STATUS_HISTORY_SUPPLIER.get()))
            .build();

    public static final Supplier<PaymentResponse> PAYMENT_RESPONSE_SUPPLIER = () -> PaymentResponse.paymentResponseWith()
            .amount(BigDecimal.valueOf(100))
            .build();

    public static final Supplier<PaymentGroupResponse> PAYMENT_GROUP_RESPONSE = () -> PaymentGroupResponse.paymentGroupDtoWith()
            .paymentGroupReference("RF-3333-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .payments(Arrays.asList(PAYMENT_RESPONSE_SUPPLIER.get()))
            .build();

    private static final IdamUserIdResponse IDAM_USER_ID_RESPONSE =
            IdamUserIdResponse.idamUserIdResponseWith().uid("1").givenName("XX").familyName("YY").name("XX YY")
                    .roles(Arrays.asList("payments-refund-approver", "payments-refund")).sub("ZZ").
                    build();

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testRefundListEmptyForCritieria() {
        when(idamService.getUserId(any())).thenReturn(IDAM_USER_ID_RESPONSE);
        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.empty());

        assertThrows(RefundListEmptyException.class, () -> refundsService.getRefundList(
                null,
                map,
                GET_REFUND_LIST_CCD_CASE_NUMBER,
                "true"
        ));
    }

    @Test
    void testRefundListForGivenCCDCaseNumber() {

        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.ofNullable(List.of(
                refundListSupplierBasedOnCCDCaseNumber1.get())));
        when(idamService.getUserId(any())).thenReturn(IDAM_USER_ID_RESPONSE);
        when(idamService.getUsersForRoles(any(), any())).thenReturn(Arrays.asList(UserIdentityDataDto.userIdentityDataWith()
                .fullName("ccd-full-name")
                .emailId("j@mail.com")
                .id("1f2b7025-0f91-4737-92c6-b7a9baef14c6")
                .build()));
        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().code("RR001").name("duplicate payment").build()));

        RefundListDtoResponse refundListDtoResponse = refundsService.getRefundList(
                null,
                map,
                GET_REFUND_LIST_CCD_CASE_NUMBER,
                "true"
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(1, refundListDtoResponse.getRefundList().size());
        assertEquals("ccd-full-name", refundListDtoResponse.getRefundList().get(0).getUserFullName());
        assertEquals("j@mail.com", refundListDtoResponse.getRefundList().get(0).getEmailId());

    }

    @Test
    void testRefundListForRefundSubmittedStatusExcludeCurrentUserTrue() {
        when(idamService.getUserId(any())).thenReturn(IDAM_USER_ID_RESPONSE);
        when(refundsRepository.findByRefundStatusAndCreatedByIsNot(
                any(),
                anyString()
        ))
                .thenReturn(Optional.ofNullable(List.of(
                        refundListSupplierForSubmittedStatus.get())));
        when(idamService.getUsersForRoles(any(), any())).thenReturn(Arrays.asList(UserIdentityDataDto.userIdentityDataWith()
                .fullName("ccd-full-name-for-submitted-status")
                .emailId("j@mail.com")
                .id("2f2b7025-0f91-4737-92c6-b7a9baef14c6")
                .build()));

        RefundListDtoResponse refundListDtoResponse = refundsService.getRefundList(
                "sent for approval",
                map,
                "",
                "true"
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(1, refundListDtoResponse.getRefundList().size());
        assertEquals(RefundStatus.SENTFORAPPROVAL, refundListDtoResponse.getRefundList().get(0).getRefundStatus());
        assertEquals(
                "ccd-full-name-for-submitted-status", refundListDtoResponse.getRefundList().get(0).getUserFullName()
        );

    }

    @Test
    void testRefundListForRefundSubmittedStatusExcludeCurrentUserFalse() {
        when(refundsRepository.findByRefundStatus(
                RefundStatus.SENTFORAPPROVAL
        ))
                .thenReturn(Optional.ofNullable(List.of(
                        refundListSupplierBasedOnCCDCaseNumber1.get(),
                        refundListSupplierForSubmittedStatus.get()
                )));

        when(idamService.getUserId(map)).thenReturn(IDAM_USER_ID_RESPONSE);

        when(idamService.getUsersForRoles(any(), any())).thenReturn(Arrays.asList(
                UserIdentityDataDto.userIdentityDataWith().fullName("ccd-full-name").emailId("h@mail.com")
                        .id(GET_REFUND_LIST_CCD_CASE_USER_ID1).build(),
                UserIdentityDataDto.userIdentityDataWith().fullName("ccd-full-name-for-submitted-status")
                        .emailId("h@mail.com").id(GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID).build()));

        when(refundReasonRepository.findByCode(anyString())).thenReturn(
                Optional.of(RefundReason.refundReasonWith().code("RR001").name("duplicate payment").build()));

        RefundListDtoResponse refundListDtoResponse = refundsService.getRefundList(
                "sent for approval",
                map,
                "",
                "false"
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(2, refundListDtoResponse.getRefundList().size());
        assertEquals(RefundStatus.SENTFORAPPROVAL, refundListDtoResponse.getRefundList().get(0).getRefundStatus());

        assertTrue(refundListDtoResponse.getRefundList().stream()
                .anyMatch(refundListDto -> refundListDto.getUserFullName().equalsIgnoreCase("ccd-full-name")));

        assertTrue(refundListDtoResponse.getRefundList().stream()
                .anyMatch(refundListDto -> refundListDto.getUserFullName().equalsIgnoreCase(
                        "ccd-full-name-for-submitted-status")));

    }

    @Test
    void givenReferenceIsNull_whenGetStatusHistory_thenNullIsReceived() {
        List<StatusHistoryDto> statusHistoryDtoList = refundsService.getStatusHistory(null, null);
        assertEquals(new ArrayList<>(), statusHistoryDtoList);
    }

    @Test
    void givenRefundIsNotFound_whenGetStatusHistory_thenRefundNotFoundExceptionIsReceived() {
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenThrow(RefundNotFoundException.class);
        assertThrows(RefundNotFoundException.class, () -> refundsService.getStatusHistory(null, "123"));
    }

    @Test
    void givenStatusHistoryIsFound_whenGetStatusHistory_thenStatusHistoryDtoListIsReceived() {

        StatusHistory statusHistory = StatusHistory.statusHistoryWith()
                .id(1)
                .refund(
                        refundListSupplierBasedOnCCDCaseNumber1.get())
                .status("AAA")
                .notes("BBB")
                .dateCreated(Timestamp.valueOf("2021-10-10 10:10:10"))
                .createdBy("CCC")
                .build();
        List<StatusHistory> statusHistories = new ArrayList<>();
        statusHistories.add(statusHistory);
        UserIdentityDataDto userIdentityDataDto = new UserIdentityDataDto();
        userIdentityDataDto.setFullName("Forename Surname");

        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refundListSupplierBasedOnCCDCaseNumber1.get());
        when(statusHistoryRepository.findByRefundOrderByDateCreatedDesc(any())).thenReturn(statusHistories);
        when(idamService.getUserIdentityData(any(), anyString())).thenReturn(userIdentityDataDto);

        List<StatusHistoryDto> statusHistoryDtoList = refundsService.getStatusHistory(null, "123");

        assertEquals(1, statusHistoryDtoList.size());
        assertEquals(1, statusHistoryDtoList.get(0).getId());
        assertEquals(1, statusHistoryDtoList.get(0).getRefundsId());
        assertEquals("AAA", statusHistoryDtoList.get(0).getStatus());
        assertEquals("BBB", statusHistoryDtoList.get(0).getNotes());
        assertEquals(Timestamp.valueOf("2021-10-10 10:10:10.0"), statusHistoryDtoList.get(0).getDateCreated());
        assertEquals("Forename Surname", statusHistoryDtoList.get(0).getCreatedBy());
    }

    @Test
    void givenStatusHistoriesAreFound_whenGetStatusHistory_thenSortedStatusHistoryDtoListIsReceived() {

        StatusHistory statusHistory1 = StatusHistory.statusHistoryWith()
                .id(1)
                .refund(
                        refundListSupplierBasedOnCCDCaseNumber1.get())
                .status("AAA")
                .notes("BBB")
                .dateCreated(Timestamp.valueOf("2021-10-10 10:10:10"))
                .createdBy("CCC")
                .build();
        StatusHistory statusHistory2 = StatusHistory.statusHistoryWith()
                .id(2)
                .refund(
                        refundListSupplierBasedOnCCDCaseNumber1.get())
                .status("DDD")
                .notes("EEE")
                .dateCreated(Timestamp.valueOf("2021-09-09 10:10:10"))
                .createdBy("FFF")
                .build();
        List<StatusHistory> statusHistories = new ArrayList<>();
        statusHistories.add(statusHistory1);
        statusHistories.add(statusHistory2);

        UserIdentityDataDto userIdentityDataDto = new UserIdentityDataDto();
        userIdentityDataDto.setFullName("Forename Surname");

        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refundListSupplierBasedOnCCDCaseNumber1.get());
        when(statusHistoryRepository.findByRefundOrderByDateCreatedDesc(any())).thenReturn(statusHistories);
        when(idamService.getUserIdentityData(any(), anyString())).thenReturn(userIdentityDataDto);

        List<StatusHistoryDto> statusHistoryDtoList = refundsService.getStatusHistory(null, "123");

        assertEquals(2, statusHistoryDtoList.size());
        assertEquals(2, statusHistoryDtoList.get(1).getId());
        assertEquals(1, statusHistoryDtoList.get(1).getRefundsId());
        assertEquals("DDD", statusHistoryDtoList.get(1).getStatus());
        assertEquals("EEE", statusHistoryDtoList.get(1).getNotes());
        assertEquals(Timestamp.valueOf("2021-09-09 10:10:10.0"), statusHistoryDtoList.get(1).getDateCreated());
        assertEquals("Forename Surname", statusHistoryDtoList.get(0).getCreatedBy());
        assertEquals(1, statusHistoryDtoList.get(0).getId());
        assertEquals(1, statusHistoryDtoList.get(0).getRefundsId());
        assertEquals("AAA", statusHistoryDtoList.get(0).getStatus());
        assertEquals("BBB", statusHistoryDtoList.get(0).getNotes());
        assertEquals(Timestamp.valueOf("2021-10-10 10:10:10.0"), statusHistoryDtoList.get(0).getDateCreated());
        assertEquals("Forename Surname", statusHistoryDtoList.get(0).getCreatedBy());
    }

    @Test
    void givenRefundIsNotFound_whenResubmitRefund_thenRefundNotFoundExceptionIsReceived() {
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenThrow(RefundNotFoundException.class);
        ResubmitRefundRequest resubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
            .amount(BigDecimal.valueOf(10))
            .refundReason("new reason")
            .build();
        assertThrows(RefundNotFoundException.class,
                () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));
    }

    @Test
    void givenRefundWithSubmitStatus_whenResubmitRefund_thenActionNotFoundExceptionIsReceived() {
        when(refundsRepository.findByReferenceOrThrow(anyString()))
                .thenReturn(refundListSupplierForSubmittedStatus.get());
        ResubmitRefundRequest resubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
                                                        .amount(BigDecimal.valueOf(10))
                                                        .refundReason("new reason")
                                                        .build();

        Exception exception = assertThrows(ActionNotFoundException.class,
                () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Action not allowed to proceed"));
    }

    @Test
    void givenRefundWithSentForApprovalStateAndWithoutReasonInResubmitRequestThrowsInvalidRefundRequestException(){
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(refundListSupplierForSendBackStatus.get());
        ResubmitRefundRequest resubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
            .amount(BigDecimal.valueOf(10))
            .build();

        Exception exception = assertThrows(InvalidRefundRequestException.class,
                                           () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Refund reason is required"));
    }

    @Test
    void givenFalsePayhubRemissionUpdateResponse_whenResubmitRefund_thenInvalidActionNotFoundExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest("RR003", BigDecimal.valueOf(400));
        resubmitRefundRequest.setAmount(BigDecimal.valueOf(400));
        when(refundsRepository.findByReferenceOrThrow(anyString()))
                .thenReturn(refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
                .thenReturn(PAYMENT_GROUP_RESPONSE.get());
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(false);
        when(refundReasonRepository.findByCodeOrThrow(anyString()))
                .thenReturn(RefundReason.refundReasonWith().name("RR001").build());

        Exception exception = assertThrows(ActionNotFoundException.class,
                () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Action not allowed to proceed"));
    }

    @Test
    void givenReasonTypeNotFound_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest validresubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
                                                            .refundReason("RRII3")
                                                            .amount(BigDecimal.valueOf(10))
                                                            .build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
                .thenReturn(refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
                .thenReturn(PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenThrow(RefundReasonNotFoundException.class);

        assertThrows(RefundReasonNotFoundException.class,
                () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", validresubmitRefundRequest, null));
    }

    @Test
    void givenInvalidReason_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest("Other - ", BigDecimal.valueOf(100));
        RefundReason refundReason =
                RefundReason.refundReasonWith().code("A").description("AA").name("Other - AA").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
                .thenReturn(refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
                .thenReturn(PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);

        Exception exception = assertThrows(InvalidRefundRequestException.class,
                () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("reason required"));
    }

    @Test
    void givenValidReasonOther_whenResubmitRefund_thenValidResponseIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest("RR035-ABCDEG", BigDecimal.valueOf(100));
        RefundReason refundReason =
                RefundReason.refundReasonWith().code("A").description("AA").name("Other - AA").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
                .thenReturn(refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
                .thenReturn(PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);
        when(idamService.getUserId(any())).thenReturn(IDAM_USER_ID_RESPONSE);

        ResubmitRefundResponseDto response = refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null);

        assertEquals("RF-3333-2234-1077-1123", response.getRefundReference());
        assertEquals(BigDecimal.valueOf(100), response.getRefundAmount());
        assertEquals("RF-3333-2234-1077-1123", response.getRefundReference());
    }

    @Test
    void givenValidReasonRR_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest("RR035-ABCDEG", BigDecimal.valueOf(100));
        RefundReason refundReason =
                RefundReason.refundReasonWith().code("RR001").description("Amended claim").name("The claim is amended").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
                .thenReturn(refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
                .thenReturn(PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);

        Exception exception = assertThrows(InvalidRefundRequestException.class,
                () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Invalid reason selected"));
    }

    @Test
    void givenValidInput_whenResubmitRefund_thenRefundStatusUpdated() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest("AAA", BigDecimal.valueOf(100));
        resubmitRefundRequest.setAmount(BigDecimal.valueOf(100));
        resubmitRefundRequest.setRefundReason("AAA");
        RefundReason refundReason =
                RefundReason.refundReasonWith().code("BBB").description("CCC").name("DDD").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
                .thenReturn(refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
                .thenReturn(PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(idamService.getUserId(any())).thenReturn(IDAM_USER_ID_RESPONSE);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);

        ResubmitRefundResponseDto response =
                refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(100), response.getRefundAmount());
        assertEquals("RF-3333-2234-1077-1123", response.getRefundReference());
    }

//    @Test
//    void givenUserIdFound_whenResubmitRefund_thenActionNotFoundExceptionIsReceived() {
//        when(refundsRepository.findByReferenceOrThrow(anyString()))
//                .thenReturn(refundListSupplierForSubmittedStatus.get());
//
//        Exception exception = assertThrows(ActionNotFoundException.class,
//                () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", new ResubmitRefundRequest(), null));
//        String actualMessage = exception.getMessage();
//        assertTrue(actualMessage.contains("Action not allowed to proceed"));
//    }

    private ResubmitRefundRequest buildResubmitRefundRequest(String refundReason, BigDecimal amount) {
        return ResubmitRefundRequest.ResubmitRefundRequestWith().refundReason(refundReason).amount(amount).build();
    }

    @Test
    void givenValidRole_whenGetRefundList_thenFilteredRefundsListIsReceived() {

        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.ofNullable(List.of(
                refundListSupplierBasedOnCCDCaseNumber1.get(), refundListSupplierBasedOnCCDCaseNumber2.get(),
                refundListSupplierBasedOnCCDCaseNumber3.get())));
        when(idamService.getUserId(any())).thenReturn(IDAM_USER_ID_RESPONSE);
        when(refundReasonRepository.findByCode(anyString())).thenReturn(
                Optional.of(RefundReason.refundReasonWith().code("RR001").name("duplicate payment").build()));
        UserIdentityDataDto dto = UserIdentityDataDto.userIdentityDataWith()
                .fullName("ccd-full-name")
                .emailId("j@mail.com")
                .id(GET_REFUND_LIST_CCD_CASE_USER_ID1)
                .build();
        when(idamService.getUsersForRoles(any(), any())).thenReturn(Arrays.asList(dto));

        RefundListDtoResponse refundListDtoResponse = refundsService.getRefundList(
                null,
                map,
                GET_REFUND_LIST_CCD_CASE_NUMBER,
                "true"
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(1, refundListDtoResponse.getRefundList().size());
        assertEquals("ccd-full-name", refundListDtoResponse.getRefundList().get(0).getUserFullName());
        assertEquals("j@mail.com", refundListDtoResponse.getRefundList().get(0).getEmailId());
    }

    @Test
    void givenEmptyRefundList_whenGetRefundList_thenRefundListEmptyExceptionIsReceived() {

        when(idamService.getUserId(any())).thenReturn(IDAM_USER_ID_RESPONSE);
        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.empty());

        Exception exception = assertThrows(RefundListEmptyException.class,
                () -> refundsService.getRefundList(
                        null,
                        map,
                        GET_REFUND_LIST_CCD_CASE_NUMBER,
                        ""));
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Refund list is empty for given criteria"));
    }

}