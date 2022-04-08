package uk.gov.hmcts.reform.refunds.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundFeeDto;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ResubmitRefundResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.exceptions.ActionNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundReasonNotFoundException;
import uk.gov.hmcts.reform.refunds.mapper.RefundFeeMapper;
import uk.gov.hmcts.reform.refunds.mapper.RefundResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.StatusHistoryResponseMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
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
import uk.gov.hmcts.reform.refunds.utils.ReferenceUtil;
import uk.gov.hmcts.reform.refunds.utils.Utility;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
class RefundServiceImplTest {

    @InjectMocks
    private RefundsServiceImpl refundsService;
    @MockBean
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
    @Mock
    private ReferenceUtil referenceUtil;
    @Spy
    private StatusHistoryResponseMapper statusHistoryResponseMapper;
    @Spy
    private RefundResponseMapper refundResponseMapper;
    @Spy
    private RefundFeeMapper refundFeeMapper;

    @Mock
    private ContextStartListener contextStartListener;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testRefundListEmptyForCriteria() {
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.empty());

        assertThrows(RefundListEmptyException.class, () -> refundsService.getRefundList(
            null,
            map,
            Utility.GET_REFUND_LIST_CCD_CASE_NUMBER,
            "true"
        ));
    }

    @Test
    void testRefundListForGivenCcdCaseNumber() {
        refundResponseMapper.setRefundFeeMapper(refundFeeMapper);
        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.ofNullable(List.of(
            Utility.refundListSupplierBasedOnCCDCaseNumber1.get())));
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(refundReasonRepository.findAll()).thenReturn(
                Collections.singletonList(RefundReason.refundReasonWith().code("RR001").name("Amended court").build()));
        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        userMap.put("payments-refund", Collections.singletonList(UserIdentityDataDto.userIdentityDataWith()
                .fullName("ccd-full-name")
                .emailId("j@mail.com")
                .id("1f2b7025-0f91-4737-92c6-b7a9baef14c6")
                .build()));
        when(contextStartListener.getUserMap()).thenReturn(userMap);

        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().code(
            "RR001").name("duplicate payment").build()));

        RefundListDtoResponse refundListDtoResponse = refundsService.getRefundList(
            null,
            map,
            Utility.GET_REFUND_LIST_CCD_CASE_NUMBER,
            "true"
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(1, refundListDtoResponse.getRefundList().size());
        assertEquals("ccd-full-name", refundListDtoResponse.getRefundList().get(0).getUserFullName());
        assertEquals("j@mail.com", refundListDtoResponse.getRefundList().get(0).getEmailId());

    }

    @Test
    void testRefundListForRefundSubmittedStatusExcludeCurrentUserTrue() {
        refundResponseMapper.setRefundFeeMapper(refundFeeMapper);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(refundsRepository.findByRefundStatusAndUpdatedByIsNot(
            any(),
            anyString()
        ))
            .thenReturn(Optional.ofNullable(List.of(
                Utility.refundListSupplierForSubmittedStatus.get())));
        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        userMap.put("payments-refund", Collections.singletonList(UserIdentityDataDto.userIdentityDataWith()
                .fullName("ccd-full-name-for-submitted-status")
                .emailId("j@mail.com")
                .id("2f2b7025-0f91-4737-92c6-b7a9baef14c6")
                .build()));
        when(contextStartListener.getUserMap()).thenReturn(userMap);

        RefundListDtoResponse refundListDtoResponse = refundsService.getRefundList(
            "Sent for approval",
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
        refundResponseMapper.setRefundFeeMapper(refundFeeMapper);
        when(refundsRepository.findByRefundStatus(
            RefundStatus.SENTFORAPPROVAL
        ))
            .thenReturn(Optional.ofNullable(List.of(
                Utility.refundListSupplierBasedOnCCDCaseNumber1.get(),
                Utility.refundListSupplierForSubmittedStatus.get()
            )));

        when(idamService.getUserId(map)).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        userMap.put("payments-refund",Arrays.asList(
            UserIdentityDataDto.userIdentityDataWith().fullName("ccd-full-name").emailId("h@mail.com")
                .id(Utility.GET_REFUND_LIST_CCD_CASE_USER_ID1).build(),
            UserIdentityDataDto.userIdentityDataWith().fullName("ccd-full-name-for-submitted-status")
                .emailId("h@mail.com").id(Utility.GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID).build()
        ));
        when(contextStartListener.getUserMap()).thenReturn(userMap);

        when(refundReasonRepository.findByCode(anyString())).thenReturn(
            Optional.of(RefundReason.refundReasonWith().code("RR001").name("duplicate payment").build()));
        when(refundReasonRepository.findAll()).thenReturn(
                Collections.singletonList(RefundReason.refundReasonWith().code("RR001").name("Amended court").build()));

        RefundListDtoResponse refundListDtoResponse = refundsService.getRefundList(
            "Sent for approval",
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
        StatusHistoryResponseDto statusHistoryResponseDto = refundsService.getStatusHistory(null, null);
        assertThat(statusHistoryResponseDto.getStatusHistoryDtoList()).isEmpty();
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
                Utility.refundListSupplierBasedOnCCDCaseNumber1.get())
            .status("AAA")
            .notes("BBB")
            .dateCreated(Timestamp.valueOf("2021-10-10 10:10:10"))
            .createdBy("CCC")
            .build();
        List<StatusHistory> statusHistories = new ArrayList<>();
        statusHistories.add(statusHistory);
        UserIdentityDataDto userIdentityDataDto = new UserIdentityDataDto();
        userIdentityDataDto.setFullName("Forename Surname");

        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(Utility.refundListSupplierBasedOnCCDCaseNumber1.get());
        when(statusHistoryRepository.findByRefundOrderByDateCreatedDesc(any())).thenReturn(statusHistories);
        when(idamService.getUserId(map)).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(idamService.getUserIdentityData(any(), anyString())).thenReturn(userIdentityDataDto);

        StatusHistoryResponseDto statusHistoryResponseDto = refundsService.getStatusHistory(map, "123");

        assertEquals(false, statusHistoryResponseDto.getLastUpdatedByCurrentUser());
        assertEquals(1, statusHistoryResponseDto.getStatusHistoryDtoList().size());
        assertEquals(1, statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getId());
        assertEquals(1, statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getRefundsId());
        assertEquals("AAA", statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getStatus());
        assertEquals("BBB", statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getNotes());
        assertEquals(
            Timestamp.valueOf("2021-10-10 10:10:10.0"),
            statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getDateCreated()
        );
        assertEquals("Forename Surname", statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getCreatedBy());
    }

    @Test
    void givenStatusHistoriesAreFound_whenGetStatusHistory_thenSortedStatusHistoryDtoListIsReceived() {

        StatusHistory statusHistory1 = StatusHistory.statusHistoryWith()
            .id(1)
            .refund(
                Utility.refundListSupplierBasedOnCCDCaseNumber1.get())
            .status("AAA")
            .notes("BBB")
            .dateCreated(Timestamp.valueOf("2021-10-10 10:10:10"))
            .createdBy("CCC")
            .build();
        StatusHistory statusHistory2 = StatusHistory.statusHistoryWith()
            .id(2)
            .refund(
                Utility.refundListSupplierBasedOnCCDCaseNumber1.get())
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

        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(Utility.refundListSupplierBasedOnCCDCaseNumber1.get());
        when(statusHistoryRepository.findByRefundOrderByDateCreatedDesc(any())).thenReturn(statusHistories);
        when(idamService.getUserId(map)).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(idamService.getUserIdentityData(any(), anyString())).thenReturn(userIdentityDataDto);

        StatusHistoryResponseDto statusHistoryResponseDto = refundsService.getStatusHistory(map, "123");

        assertEquals(false, statusHistoryResponseDto.getLastUpdatedByCurrentUser());
        assertEquals(2, statusHistoryResponseDto.getStatusHistoryDtoList().size());
        assertEquals(2, statusHistoryResponseDto.getStatusHistoryDtoList().get(1).getId());
        assertEquals(1, statusHistoryResponseDto.getStatusHistoryDtoList().get(1).getRefundsId());
        assertEquals("DDD", statusHistoryResponseDto.getStatusHistoryDtoList().get(1).getStatus());
        assertEquals("EEE", statusHistoryResponseDto.getStatusHistoryDtoList().get(1).getNotes());
        assertEquals(
            Timestamp.valueOf("2021-09-09 10:10:10.0"),
            statusHistoryResponseDto.getStatusHistoryDtoList().get(1).getDateCreated()
        );
        assertEquals("Forename Surname", statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getCreatedBy());
        assertEquals(1, statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getId());
        assertEquals(1, statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getRefundsId());
        assertEquals("AAA", statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getStatus());
        assertEquals("BBB", statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getNotes());
        assertEquals(
            Timestamp.valueOf("2021-10-10 10:10:10.0"),
            statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getDateCreated()
        );
        assertEquals("Forename Surname", statusHistoryResponseDto.getStatusHistoryDtoList().get(0).getCreatedBy());
    }

    @Test
    void givenRefundIsNotFound_whenResubmitRefund_thenRefundNotFoundExceptionIsReceived() {
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenThrow(RefundNotFoundException.class);
        ResubmitRefundRequest resubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
            .amount(BigDecimal.valueOf(10))
            .refundReason("new reason")
            .contactDetails(ContactDetails.contactDetailsWith()
                                .addressLine("High Street 112")
                                .country("UK")
                                .county("Londonshire")
                                .city("London")
                                .postalCode("P1 1PO")
                                .email("person@somemail.com")
                                .notificationType("EMAIL")
                                .build())

            .build();
        assertThrows(
            RefundNotFoundException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null)
        );
    }

    @Test
    void givenRefundWithSubmitStatus_whenResubmitRefund_thenActionNotFoundExceptionIsReceived() {
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSubmittedStatus.get());
        ResubmitRefundRequest resubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
            .amount(BigDecimal.valueOf(10))
            .refundReason("new reason")
            .contactDetails(ContactDetails.contactDetailsWith()
                                .addressLine("High Street 112")
                                .country("UK")
                                .county("Londonshire")
                                .city("London")
                                .postalCode("P1 1PO")
                                .email("person@somemail.com")
                                .notificationType("EMAIL")
                                .build())

            .build();

        Exception exception = assertThrows(
            ActionNotFoundException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null)
        );

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Action not allowed to proceed"));
    }

    @Test
    void givenRefundWithSentForApprovalStateAndWithoutReasonInResubmitRequestThrowsInvalidRefundRequestException() {
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatusForNullReason.get());
        ResubmitRefundRequest resubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
            .amount(BigDecimal.valueOf(10))
            .contactDetails(ContactDetails.contactDetailsWith()
                                .addressLine("High Street 112")
                                .country("UK")
                                .county("Londonshire")
                                .city("London")
                                .postalCode("P1 1PO")
                                .email("person@somemail.com")
                                .notificationType("EMAIL")
                                .build())

            .build();

        Exception exception = assertThrows(
            InvalidRefundRequestException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null)
        );

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Refund reason is required"));
    }

    @Test
    void givenFalsePayhubRemissionUpdateResponse_whenResubmitRefund_thenInvalidActionNotFoundExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest("RR036", BigDecimal.valueOf(400));
        resubmitRefundRequest.setAmount(BigDecimal.valueOf(400));
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .addressLine("High Street 112")
                                                    .country("UK")
                                                    .county("Londonshire")
                                                    .city("London")
                                                    .postalCode("P1 1PO")
                                                    .email("person@somemail.com")
                                                    .notificationType("EMAIL")
                                                    .build());

        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(false);
        when(refundReasonRepository.findByCodeOrThrow(anyString()))
            .thenReturn(RefundReason.refundReasonWith().name("RR001").build());

        Exception exception = assertThrows(
            ActionNotFoundException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null)
        );

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Action not allowed to proceed"));
    }

    @Test
    void givenReasonTypeNotFound_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {

        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenThrow(RefundReasonNotFoundException.class);
        ResubmitRefundRequest validResubmitRefundRequest = ResubmitRefundRequest.ResubmitRefundRequestWith()
            .refundReason("RRII3")
            .amount(BigDecimal.valueOf(10))
            .contactDetails(ContactDetails.contactDetailsWith()
                                .addressLine("High Street 112")
                                .country("UK")
                                .county("Londonshire")
                                .city("London")
                                .postalCode("P1 1PO")
                                .email("person@somemail.com")
                                .notificationType("EMAIL")
                                .build())

            .build();
        assertThrows(
            RefundReasonNotFoundException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", validResubmitRefundRequest, null)
        );
    }

    @Test
    void givenInvalidReason_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest("Other - ", BigDecimal.valueOf(100));

        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .addressLine("High Street 112")
                                                    .country("UK")
                                                    .county("Londonshire")
                                                    .city("London")
                                                    .postalCode("P1 1PO")
                                                    .email("person@somemail.com")
                                                    .notificationType("EMAIL")
                                                    .build());
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("A").description("AA").name("Other - AA").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);

        Exception exception = assertThrows(
            InvalidRefundRequestException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null)
        );
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("reason required"));
    }

    @Test
    void givenValidReasonOther_whenResubmitRefund_thenValidResponseIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest(
            "RR035-ABCDEG",
            BigDecimal.valueOf(100));
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .addressLine("High Street 112")
                                                    .country("UK")
                                                    .county("Londonshire")
                                                    .city("London")
                                                    .postalCode("P1 1PO")
                                                    .email("person@somemail.com")
                                                    .notificationType("EMAIL")
                                                    .build());
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("A").description("AA").name("Other - AA").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);

        ResubmitRefundResponseDto response = refundsService.resubmitRefund(
            "RF-1629-8081-7517-5855",
            resubmitRefundRequest,
            null
        );

        assertEquals("RF-3333-2234-1077-1123", response.getRefundReference());
        assertEquals(BigDecimal.valueOf(100), response.getRefundAmount());
        assertEquals("RF-3333-2234-1077-1123", response.getRefundReference());
    }

    @Test
    void givenValidReasonRR_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest(
            "RR035-ABCDEG",
            BigDecimal.valueOf(100));
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .addressLine("High Street 112")
                                                    .country("UK")
                                                    .county("Londonshire")
                                                    .city("London")
                                                    .postalCode("P1 1PO")
                                                    .email("person@somemail.com")
                                                    .notificationType("EMAIL")
                                                    .build());

        RefundReason refundReason =
            RefundReason.refundReasonWith().code("RR001").description("Amended claim").name("The claim is amended").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);

        Exception exception = assertThrows(
            InvalidRefundRequestException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null)
        );
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Invalid reason selected"));
    }

    @Test
    void givenValidInput_whenResubmitRefund_thenRefundStatusUpdated() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest("AAA", BigDecimal.valueOf(100));
        resubmitRefundRequest.setAmount(BigDecimal.valueOf(100));
        resubmitRefundRequest.setRefundReason("AAA");
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .addressLine("High Street 112")
                                                    .country("UK")
                                                    .county("Londonshire")
                                                    .city("London")
                                                    .postalCode("P1 1PO")
                                                    .email("person@somemail.com")
                                                    .notificationType("EMAIL")
                                                    .build());
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("BBB").description("CCC").name("DDD").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);

        ResubmitRefundResponseDto response =
            refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(100), response.getRefundAmount());
        assertEquals("RF-3333-2234-1077-1123", response.getRefundReference());
    }

    private ResubmitRefundRequest buildResubmitRefundRequest(String refundReason, BigDecimal amount) {
        return ResubmitRefundRequest.ResubmitRefundRequestWith()
            .refundReason(refundReason)
            .amount(amount)
            .refundFees(Collections.singletonList(
                    RefundFeeDto.refundFeeRequestWith()
                            .feeId(1)
                            .code("RR001")
                            .version("1.0")
                            .volume(1)
                            .refundAmount(amount)
                            .build()))
            .build();
    }

    @Test
    void givenValidRole_whenGetRefundList_thenFilteredRefundsListIsReceived() {
        refundResponseMapper.setRefundFeeMapper(refundFeeMapper);
        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.ofNullable(List.of(
            Utility.refundListSupplierBasedOnCCDCaseNumber1.get(), Utility.refundListSupplierBasedOnCCDCaseNumber2.get())));
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(idamService.getUserIdentityData(any(),anyString()))
            .thenReturn(UserIdentityDataDto.userIdentityDataWith().id("userId2").fullName("mock2-Forename mock2-Surname")
                            .emailId("mock2fullname@gmail.com").build());
        when(refundReasonRepository.findByCode(anyString())).thenReturn(
            Optional.of(RefundReason.refundReasonWith().code("RR001").name("duplicate payment").build()));
        when(refundReasonRepository.findAll()).thenReturn(
                Collections.singletonList(RefundReason.refundReasonWith().code("RR001").name("Amended court").build()));
        UserIdentityDataDto dto = UserIdentityDataDto.userIdentityDataWith()
            .fullName("ccd-full-name")
            .emailId("j@mail.com")
            .id(Utility.GET_REFUND_LIST_CCD_CASE_USER_ID1)
            .build();
        Map<String, List<UserIdentityDataDto>> userMap = new ConcurrentHashMap<>();
        userMap.put("payments-refund", Collections.singletonList(dto));
        when(contextStartListener.getUserMap()).thenReturn(userMap);
        RefundListDtoResponse refundListDtoResponse = refundsService.getRefundList(
            null,
            map,
            Utility.GET_REFUND_LIST_CCD_CASE_NUMBER,
            "true"
        );

        assertNotNull(refundListDtoResponse);
        assertEquals(2, refundListDtoResponse.getRefundList().size());
        assertEquals("ccd-full-name", refundListDtoResponse.getRefundList().get(0).getUserFullName());
        assertEquals("j@mail.com", refundListDtoResponse.getRefundList().get(0).getEmailId());
    }

    @Test
    void givenEmptyRefundList_whenGetRefundList_thenRefundListEmptyExceptionIsReceived() {

        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.empty());

        Exception exception = assertThrows(
            RefundListEmptyException.class,
            () -> refundsService.getRefundList(
                null,
                map,
                Utility.GET_REFUND_LIST_CCD_CASE_NUMBER,
                ""
            )
        );
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Refund list is empty for given criteria"));
    }

    @Test
    void givenEmptyNotificationType_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest(
            "RR035-ABCDEG",
            BigDecimal.valueOf(100));
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .notificationType(null)
                                                    .build());
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("A").description("AA").name("Other - AA").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);


        Exception exception = assertThrows(
            InvalidRefundRequestException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Notification should not be null or empty"));
    }

    @Test
    void givenInvalidNotificationType_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest(
            "RR035-ABCDEG",
            BigDecimal.valueOf(100));
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .notificationType("POST")
                                                    .build());
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("A").description("AA").name("Other - AA").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);


        Exception exception = assertThrows(
            InvalidRefundRequestException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Contact details should be email or letter"));
    }

    @Test
    void givenNullEmailForNotificationType_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest(
            "RR035-ABCDEG",
            BigDecimal.valueOf(100));
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .notificationType("EMAIL")
                                                    .email(null)
                                                    .build());
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("A").description("AA").name("Other - AA").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);


        Exception exception = assertThrows(
            InvalidRefundRequestException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Email id should not be empty"));
    }

    @Test
    void givenNullPostalCodeForNotificationType_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest(
            "RR035-ABCDEG",
            BigDecimal.valueOf(100));
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .notificationType("LETTER")
                                                    .postalCode(null)
                                                    .build());
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("A").description("AA").name("Other - AA").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);


        Exception exception = assertThrows(
            InvalidRefundRequestException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Postal code should not be empty"));
    }

    @Test
    void givenInvalidEmailForNotificationType_whenResubmitRefund_thenInvalidRefundRequestExceptionIsReceived() {
        ResubmitRefundRequest resubmitRefundRequest = buildResubmitRefundRequest(
            "RR035-ABCDEG",
            BigDecimal.valueOf(100));
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .notificationType("EMAIL")
                                                    .email("test@")
                                                    .build());
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("A").description("AA").name("Other - AA").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);


        Exception exception = assertThrows(
            InvalidRefundRequestException.class,
            () -> refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Email id is not valid"));
    }

    @Test
    void givenValidAmountInput_whenResubmitRefund_thenRefundStatusUpdated() {
        ResubmitRefundRequest resubmitRefundRequest = new ResubmitRefundRequest();
        resubmitRefundRequest.setAmount(BigDecimal.valueOf(100));
        resubmitRefundRequest.setRefundFees(Collections.singletonList(
                RefundFeeDto.refundFeeRequestWith()
                        .feeId(1)
                        .code("RR001")
                        .version("1.0")
                        .volume(1)
                        .refundAmount(new BigDecimal(1))
                        .build()));
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("BBB").description("CCC").name("DDD").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);

        ResubmitRefundResponseDto response =
            refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(100), response.getRefundAmount());
        assertEquals("RF-3333-2234-1077-1123", response.getRefundReference());
    }

    @Test
    void givenValidReasonInput_whenResubmitRefund_thenRefundStatusUpdated() {
        ResubmitRefundRequest resubmitRefundRequest = new ResubmitRefundRequest();
        resubmitRefundRequest.setRefundFees(Collections.singletonList(
                RefundFeeDto.refundFeeRequestWith()
                        .feeId(1)
                        .code("RR001")
                        .version("1.0")
                        .volume(1)
                        .refundAmount(new BigDecimal(1))
                        .build()));
        resubmitRefundRequest.setRefundReason("RR002");
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("RR001").description("The claim is amended").name("Amended claim").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);

        ResubmitRefundResponseDto response =
            refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null);

        assertNotNull(response);
        assertEquals("RF-3333-2234-1077-1123", response.getRefundReference());
    }

    @Test
    void givenValidContactDeatilsInput_whenResubmitRefund_thenRefundStatusUpdated() {
        ResubmitRefundRequest resubmitRefundRequest = new ResubmitRefundRequest();
        resubmitRefundRequest.setContactDetails(ContactDetails.contactDetailsWith()
                                                    .addressLine("High Street 112")
                                                    .country("UK")
                                                    .county("Londonshire")
                                                    .city("London")
                                                    .postalCode("P1 1PO")
                                                    .email("person@somemail.com")
                                                    .notificationType("EMAIL")
                                                    .build());
        resubmitRefundRequest.setRefundFees(Collections.singletonList(
                RefundFeeDto.refundFeeRequestWith()
                        .feeId(1)
                        .code("RR001")
                        .version("1.0")
                        .volume(1)
                        .refundAmount(new BigDecimal(1))
                        .build()));
        RefundReason refundReason =
            RefundReason.refundReasonWith().code("RR001").description("The claim is amended").name("Amended claim").build();
        when(refundsRepository.findByReferenceOrThrow(anyString()))
            .thenReturn(Utility.refundListSupplierForSendBackStatus.get());
        when(paymentService.fetchPaymentGroupResponse(any(), anyString()))
            .thenReturn(Utility.PAYMENT_GROUP_RESPONSE.get());
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(refundReason);
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(paymentService.updateRemissionAmountInPayhub(any(), anyString(), any())).thenReturn(true);

        ResubmitRefundResponseDto response =
            refundsService.resubmitRefund("RF-1629-8081-7517-5855", resubmitRefundRequest, null);

        assertNotNull(response);
        assertEquals("RF-3333-2234-1077-1123", response.getRefundReference());
    }

    // @Test
    void givenMoreRefundAmtThanPaymentAmt_whenInitiateRefund_thenInvalidRefundRequestException() {
        RefundRequest refundRequest = RefundRequest.refundRequestWith()
                .paymentReference("1")
                .refundReason("RR005")
                .ccdCaseNumber("2")
                .refundAmount(BigDecimal.valueOf(777))
                .paymentAmount(BigDecimal.valueOf(666))
                .feeIds("3")
                .contactDetails(ContactDetails.contactDetailsWith()
                        .addressLine("ABC Street")
                        .email("mock@test.com")
                        .city("London")
                        .county("Greater London")
                        .country("UK")
                        .postalCode("E1 6AN")
                        .notificationType("Letter")
                        .build())
                .refundFees(Collections.singletonList(
                        RefundFeeDto.refundFeeRequestWith()
                                .feeId(1)
                                .code("RR001")
                                .version("1.0")
                                .volume(1)
                                .refundAmount(new BigDecimal(100))
                                .build()))
                .serviceType("AAA")
                .paymentChannel("BBB")
                .paymentMethod("CCC")
                .build();

        Exception exception = assertThrows(InvalidRefundRequestException.class, () -> refundsService.initiateRefund(refundRequest, map));
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("The amount you want to refund is more than the amount paid"));
    }

    @Test
    void givenRefundAmt_whenInitiateRefund_thenRefundResponseReceived() throws Exception {
        RefundRequest refundRequest = RefundRequest.refundRequestWith()
                .paymentReference("1")
                .refundReason("RR005")
                .ccdCaseNumber("2")
                .refundAmount(BigDecimal.valueOf(555))
                .paymentAmount(BigDecimal.valueOf(666))
                .feeIds("3")
                .contactDetails(ContactDetails.contactDetailsWith()
                        .addressLine("ABC Street")
                        .email("mock@test.com")
                        .city("London")
                        .county("Greater London")
                        .country("UK")
                        .postalCode("E1 6AN")
                        .notificationType("Letter")
                        .build())
                .refundFees(Collections.singletonList(
                        RefundFeeDto.refundFeeRequestWith()
                                .feeId(1)
                                .code("RR001")
                                .version("1.0")
                                .volume(1)
                                .refundAmount(new BigDecimal(100))
                                .build()))
                .serviceType("AAA")
                .paymentChannel("BBB")
                .paymentMethod("CCC")
                .build();

        when(refundsRepository.findByPaymentReference(anyString()))
            .thenReturn(Optional.of(Collections.singletonList(Utility.refundListSupplierBasedOnCCDCaseNumber1.get())));
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(referenceUtil.getNext(anyString())).thenReturn("RF1234567890");
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(RefundReason.refundReasonWith().name("RR007").build());

        RefundResponse refundResponse = refundsService.initiateRefund(refundRequest, map);
        assertNotNull(refundResponse);
        assertEquals("RF1234567890", refundResponse.getRefundReference());
    }

    @Test
    void givenRefundAmtFeeLessThanPaymentAmt_whenInitiateRefund_thenRefundResponseReceived() throws Exception {
        RefundRequest refundRequest = RefundRequest.refundRequestWith()
                .paymentReference("1")
                .refundReason("RR005")
                .ccdCaseNumber("2")
                .refundAmount(BigDecimal.valueOf(555))
                .paymentAmount(BigDecimal.valueOf(666))
                .feeIds("3")
                .contactDetails(ContactDetails.contactDetailsWith()
                        .addressLine("ABC Street")
                        .email("mock@test.com")
                        .city("London")
                        .county("Greater London")
                        .country("UK")
                        .postalCode("E1 6AN")
                        .notificationType("Letter")
                        .build())
                .refundFees(Collections.singletonList(
                        RefundFeeDto.refundFeeRequestWith()
                                .feeId(1)
                                .code("RR001")
                                .version("1.0")
                                .volume(1)
                                .refundAmount(new BigDecimal(100))
                                .build()))
                .serviceType("AAA")
                .paymentChannel("BBB")
                .paymentMethod("CCC")
                .build();

        when(refundsRepository.findByPaymentReference(anyString()))
                .thenReturn(Optional.of(Collections.singletonList(Utility.refundListSupplierForApprovedStatus.get())));
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(referenceUtil.getNext(anyString())).thenReturn("RF1234567890");
        when(refundReasonRepository.findByCodeOrThrow(anyString())).thenReturn(RefundReason.refundReasonWith().name("RR007").build());

        RefundResponse refundResponse = refundsService.initiateRefund(refundRequest, map);
        assertNotNull(refundResponse);
        assertEquals("RF1234567890", refundResponse.getRefundReference());
    }

    // @Test
    void givenRefundAmtFeeExceedsThanPaymentAmt_whenInitiateRefund_thenRefundResponseReceived() throws Exception {
        RefundRequest refundRequest = RefundRequest.refundRequestWith()
                .paymentReference("1")
                .refundReason("RR005")
                .ccdCaseNumber("2")
                .refundAmount(BigDecimal.valueOf(555))
                .paymentAmount(BigDecimal.valueOf(666))
                .feeIds("3")
                .contactDetails(ContactDetails.contactDetailsWith()
                        .addressLine("ABC Street")
                        .email("mock@test.com")
                        .city("London")
                        .county("Greater London")
                        .country("UK")
                        .postalCode("E1 6AN")
                        .notificationType("Letter")
                        .build())
                .refundFees(Collections.singletonList(
                        RefundFeeDto.refundFeeRequestWith()
                                .feeId(1)
                                .code("RR001")
                                .version("1.0")
                                .volume(1)
                                .refundAmount(new BigDecimal(900))
                                .build()))
                .serviceType("AAA")
                .paymentChannel("BBB")
                .paymentMethod("CCC")
                .build();

        when(refundsRepository.findByPaymentReference(anyString()))
                .thenReturn(Optional.of(Collections.singletonList(Utility.refundListSupplierForAcceptedStatus.get())));
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE);
        when(referenceUtil.getNext(anyString())).thenReturn("RF1234567890");

        Exception exception = assertThrows(InvalidRefundRequestException.class, () -> refundsService.initiateRefund(refundRequest, map));
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("The amount you want to refund is more than the amount paid"));
    }

    public static final Supplier<Refund> refundListContactDetailsEmail = () -> Refund.refundsWith()
        .id(1)
        .ccdCaseNumber("1234567890123456")
        .refundStatus(RefundStatus.APPROVED)
        .contactDetails(ContactDetails.contactDetailsWith()
                            .addressLine("ABC Street")
                            .email("mock@test.com")
                            .city("London")
                            .county("Greater London")
                            .country("UK")
                            .postalCode("E1 6AN")
                            .notificationType("EMAIL")
                            .build())
        .build();

    public static final Supplier<Refund> refundListContactDetailsLetter = () -> Refund.refundsWith()
        .id(1)
        .ccdCaseNumber("1234567890123456")
        .refundStatus(RefundStatus.APPROVED)
        .contactDetails(ContactDetails.contactDetailsWith()
                            .addressLine("ABC Street")
                            .email("mock@test.com")
                            .city("London")
                            .county("Greater London")
                            .country("UK")
                            .postalCode("E1 6AN")
                            .notificationType("Letter")
                            .build())
        .build();

}
