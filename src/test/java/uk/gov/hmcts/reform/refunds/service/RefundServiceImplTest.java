package uk.gov.hmcts.reform.refunds.service;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.config.ContextStartListener;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundFeeDto;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundSearchCriteria;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.FeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFailureReportDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;
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
import uk.gov.hmcts.reform.refunds.mapper.PaymentFailureResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.RefundFeeMapper;
import uk.gov.hmcts.reform.refunds.mapper.RefundResponseMapper;
import uk.gov.hmcts.reform.refunds.mapper.StatusHistoryResponseMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundFees;
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
import uk.gov.hmcts.reform.refunds.validator.RefundValidator;

import java.math.BigDecimal;
import java.sql.Timestamp;
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

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

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
    private List<String> paymentReferenceList;
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
    @Spy
    private PaymentFailureResponseMapper paymentFailureResponseMapper;

    @Mock
    private ContextStartListener contextStartListener;

    @MockBean
    private Specification<Refund> mockSpecification;

    @MockBean
    private List<Refund> refund;

    @MockBean
    private RefundSearchCriteria refundSearchCriteria;

    @Mock
    private Predicate predicate;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Root<Refund> root;

    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private  Expression<Date> expression;


    @Mock
    private CriteriaBuilder.In<Date> inCriteriaForStatus;

    @Mock
    private Path<String> stringPath;

    @Mock
    private RefundValidator refundValidator;

    @Value("${refund.search.days}")
    private Integer numberOfDays;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd");


    private  RefundSearchCriteria getRefundSearchCriteria() {

        return RefundSearchCriteria.searchCriteriaWith()
            .startDate(Timestamp.valueOf("2021-10-10 10:10:10"))
            .endDate(Timestamp.valueOf("2021-10-15 10:10:10"))
            .refundReference("RF-1111-2234-1077-1123")
            .build();
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
                                    .naturalAccountCode("123")
                                    .build()
                  )
            ).build();

        payments.add(payments1);
        return payments;
    }

    private List<Refund> getRefundList() {
        List<Refund> refunds = new ArrayList<>();
        Refund ref =  Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .ccdCaseNumber(Utility.GET_REFUND_LIST_CCD_CASE_NUMBER)
            .createdBy(Utility.GET_REFUND_LIST_CCD_CASE_USER_ID2)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.APPROVED)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(Utility.GET_REFUND_LIST_CCD_CASE_USER_ID2)
            .refundFees(Arrays.asList(RefundFees.refundFeesWith().refundAmount(BigDecimal.valueOf(100)).code("1").build()))
            .build();

        refunds.add(ref);
        return refunds;
    }

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    public static final Supplier<Refund> refundListSupplierBasedOnCCDCaseNumber1 = () -> Refund.refundsWith()
        .id(1)
        .amount(BigDecimal.valueOf(100))
        .ccdCaseNumber(Utility.GET_REFUND_LIST_CCD_CASE_NUMBER)
        .createdBy(Utility.GET_REFUND_LIST_CCD_CASE_USER_ID1)
        .reference("RF-1111-2234-1077-1123")
        .refundStatus(RefundStatus.SENTFORAPPROVAL)
        .reason("RR001")
        .paymentReference("RC-1111-2234-1077-1123")
        .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
        .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
        .updatedBy(Utility.GET_REFUND_LIST_CCD_CASE_USER_ID1)
        .build();

    public static final Supplier<Refund> refundListSupplierForSendBackStatus = () -> Refund.refundsWith()
        .id(3)
        .amount(BigDecimal.valueOf(300))
        .ccdCaseNumber(Utility.GET_REFUND_LIST_SENDBACK_REFUND_STATUS)
        .createdBy(Utility.GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
        .updatedBy(Utility.GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID)
        .reference("RF-3333-2234-1077-1123")
        .refundStatus(RefundStatus.UPDATEREQUIRED)
        .reason("Other")
        .paymentReference("RC-3333-2234-1077-1123")
        .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
        .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
        .statusHistories(Arrays.asList(Utility.STATUS_HISTORY_SUPPLIER.get()))
        .build();

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
        assertEquals(Collections.emptyList(), statusHistoryResponseDto.getStatusHistoryDtoList());
        assertEquals(false, statusHistoryResponseDto.getLastUpdatedByCurrentUser());
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

    @Test
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

    @Test
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

    @Test
    void testRefundListEmptyForSearchCritieria() {
        ReflectionTestUtils.setField(refundsService, "numberOfDays", numberOfDays);
        when(refundsRepository.findAll()).thenReturn(null);
        Optional<String> startDate = Optional.ofNullable(LocalDate.now().minusDays(1).toString(DATE_FORMAT));
        Optional<String> endDate = Optional.ofNullable(LocalDate.now().toString(DATE_FORMAT));

        Exception exception = assertThrows(RefundNotFoundException.class, () -> refundsService.search(startDate,endDate,"RF12345"
        ));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("No refunds available for the given date range"));
    }

    @Test
    void testRefundResponseWhenRefundListForSearchCriteraReturnsuccess() {
        RefundsServiceImpl mock = org.mockito.Mockito.mock(RefundsServiceImpl.class);
        when(mock.searchByCriteria(getRefundSearchCriteria())).thenReturn(mockSpecification);
        ReflectionTestUtils.setField(refundsService, "numberOfDays", numberOfDays);
        when(refundsRepository.findAll(any()))
            .thenReturn(getRefundList());
        List<String> referenceList = new ArrayList<>();
        referenceList.add("RC-1111-2234-1077-1123");
        when(paymentService.fetchPaymentResponse(referenceList)).thenReturn(getPayments());
        Optional<String> startDate = Optional.ofNullable(LocalDate.now().minusDays(1).toString(DATE_FORMAT));
        Optional<String> endDate = Optional.ofNullable(LocalDate.now().toString(DATE_FORMAT));
        List<RefundLiberata>
            refundListDtoResponse = refundsService.search(startDate,endDate,"RF12345");

        Assert.assertEquals("RF-1111-2234-1077-1123",refundListDtoResponse.get(0).getReference());

    }

    @Test
    void testsearchByCriteriaWhenValidInputProvided() {

        when(mockSpecification.toPredicate(root, query, builder)).thenReturn(predicate);

        when(root.<String>get("dateUpdated")).thenReturn(stringPath);
        Specification<Refund> refunds = refundsService.searchByCriteria(getRefundSearchCriteria());
        assertNotNull(refunds);
    }

    public static final Supplier<Refund> refundListLiberataTest = () -> Refund.refundsWith()
        .id(1)
        .ccdCaseNumber("1234567890123456")
        .refundStatus(RefundStatus.SENTFORAPPROVAL)
        .paymentReference("RC-1234-1234-1234-1234")
        .feeIds("1")
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
    public static final Supplier<Refund> refundListLiberataRefundsTest = () -> Refund.refundsWith()
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

    @Test
    void testgetPredicateWhenValidInputProvided() {
        when(root.<String>get("dateUpdated")).thenReturn(stringPath);
        Specification<Refund> actual = refundsService.searchByCriteria(getRefundSearchCriteria());
        Predicate actualPredicate = actual.toPredicate(root, query, builder);
        when(builder.function("date_trunc",
                              Date.class,
                              builder.literal("seconds"),
                              root.get("dateUpdated"))).thenReturn(expression);
        Predicate predicate = refundsService.getPredicate(root,builder,getRefundSearchCriteria(),query);

        assertEquals(predicate, actualPredicate);
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

    @Test
    void testGetRefundResponseDtoList() {
        when(refundReasonRepository.findAll()).thenReturn(
            Arrays.asList(RefundReason.refundReasonWith().code("RR001").name("Amended court").build()));
        when(contextStartListener.getUserMap()).thenReturn(null);
        List<UserIdentityDataDto> userIdentityDataDtoList =  Arrays.asList(UserIdentityDataDto.userIdentityDataWith()
                                                                               .fullName("ccd-full-name")
                                                                               .emailId("j@mail.com")
                                                                               .id("1f2b7025-0f91-4737-92c6-b7a9baef14c6")
                                                                               .build());
        when(idamService.getUsersForRoles(any(), any())).thenReturn(userIdentityDataDtoList);
        UserIdentityDataDto userIdentityDataDto = new UserIdentityDataDto();
        userIdentityDataDto.setFullName("Forename Surname");
        when(idamService.getUserIdentityData(any(), anyString())).thenReturn(userIdentityDataDto);
        IdamTokenResponse idamTokenResponse = IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("qwerrtyuiop").build();
        when(idamService.getSecurityTokens()).thenReturn(idamTokenResponse);
        MultiValueMap<String, String> headers = null;
        List<Refund> refundList = List.of(refundListSupplierBasedOnCCDCaseNumber1.get());
        List<String> roles = Arrays.asList("payments-refund-approver", "payments-refund");
        List<RefundDto> refundDtos = refundsService.getRefundResponseDtoList(headers, refundList, roles);
        Assertions.assertNotNull(refundDtos);
        Assertions.assertEquals(1, refundDtos.size());
        Assertions.assertEquals("RF-1111-2234-1077-1123", refundDtos.get(0).getRefundReference());
    }

    @Test
    void testPaymentFailureForEmptyCriteria() {
        when(refundsRepository.findByPaymentReferenceInAndRefundStatusNotIn(any(),any()))
            .thenReturn(Optional.empty());
        assertEquals(Optional.empty(), refundsService.getPaymentFailureReport(paymentReferenceList));
    }

    @Test
    void testPaymentFailureReportForGivenPaymentReferenceList() {

        when(refundsRepository.findByPaymentReferenceInAndRefundStatusNotIn(any(), any()))
            .thenReturn(Optional.ofNullable(List.of(refundListSupplierForSendBackStatus.get())));

        Optional<List<Refund>> refundList = refundsService.getPaymentFailureReport(paymentReferenceList);

        assertThat(refundList).isPresent();

        if (refundList.isPresent() && !refundList.get().isEmpty()) {
            PaymentFailureReportDtoResponse paymentFailureReportDtoResponse =
                refundsService.getPaymentFailureDtoResponse(refundList.get());
            assertNotNull(paymentFailureReportDtoResponse);
            assertEquals(1, paymentFailureReportDtoResponse.getPaymentFailureDto().size());
            assertEquals(
                "RC-3333-2234-1077-1123",
                paymentFailureReportDtoResponse.getPaymentFailureDto().get(0).getPaymentReference()
            );
            assertEquals(
                "RF-3333-2234-1077-1123",
                paymentFailureReportDtoResponse.getPaymentFailureDto().get(0).getRefundReference()
            );
        }
    }

    @Test
    void testRefundListForGivenCcdCaseNumberWhenRoleIsPayment() {
        refundResponseMapper.setRefundFeeMapper(refundFeeMapper);
        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.ofNullable(List.of(
            Utility.refundListSupplierBasedOnCCDCaseNumber1.get())));
        when(idamService.getUserId(any())).thenReturn(Utility.IDAM_USER_ID_RESPONSE_PAYMENT_ROLE);
        when(refundReasonRepository.findAll()).thenReturn(
            Collections.singletonList(RefundReason.refundReasonWith().code("RR001").name("Amended court").build()));
        UserIdentityDataDto userIdentityDataDto = new UserIdentityDataDto();
        userIdentityDataDto.setId("1f2b7025-0f91-4737-92c6-b7a9baef14c6");
        userIdentityDataDto.setFullName("full-name");
        userIdentityDataDto.setEmailId("j@mail.com");
        when(idamService.getUserIdentityData(any(), anyString())).thenReturn(userIdentityDataDto);
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
        assertEquals("full-name", refundListDtoResponse.getRefundList().get(0).getUserFullName());
        assertEquals("j@mail.com", refundListDtoResponse.getRefundList().get(0).getEmailId());
        assertEquals("1111-2222-3333-4444", refundListDtoResponse.getRefundList().get(0).getCcdCaseNumber());

    }
}
