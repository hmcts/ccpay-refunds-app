package uk.gov.hmcts.reform.refunds.service;


import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.exceptions.RefundListEmptyException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
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
import uk.gov.hmcts.reform.refunds.services.RefundsServiceImpl;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.Assert.*;
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


    @Spy
    private StatusHistoryResponseMapper statusHistoryResponseMapper;

    @Spy
    private RefundResponseMapper refundResponseMapper;

    public static final String GET_REFUND_LIST_CCD_CASE_NUMBER = "1111-2222-3333-4444";
    public static final String GET_REFUND_LIST_CCD_CASE_USER_ID = "1f2b7025-0f91-4737-92c6-b7a9baef14c6";

    public static final String GET_REFUND_LIST_SUBMITTED_REFUND_STATUS = "2222-2222-3333-4444";
    public static final String GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID = "2f2b7025-0f91-4737-92c6-b7a9baef14c6";

    public static final String GET_REFUND_LIST_SENDBACK_REFUND_STATUS = "3333-3333-3333-4444";
    public static final String GET_REFUND_LIST_SENDBACK_REFUND_CCD_CASE_USER_ID = "3f2b7025-0f91-4737-92c6-b7a9baef14c6";

    public static final Supplier<Refund> refundListSupplierBasedOnCCDCaseNumber = () -> Refund.refundsWith()
        .id(1)
        .amount(BigDecimal.valueOf(100))
        .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_NUMBER)
        .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID)
        .reference("RF-1111-2234-1077-1123")
        .refundStatus(RefundStatus.SENTFORAPPROVAL)
        .reason("RR001")
        .paymentReference("RC-1111-2234-1077-1123")
        .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
        .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
        .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID)
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
        .build();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testRefundListEmptyForCritieria() {
        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.empty());

        assertThrows(RefundListEmptyException.class, () -> refundsService.getRefundList(
            null,
            map,
            GET_REFUND_LIST_CCD_CASE_NUMBER,
            "true"
        ));
    }

    @Test
    void testRefundListForGivenCCDCaseNumber() throws Exception {

        when(refundsRepository.findByCcdCaseNumber(anyString())).thenReturn(Optional.ofNullable(List.of(
            refundListSupplierBasedOnCCDCaseNumber.get())));
        when(idamService.getUserId(map)).thenReturn(GET_REFUND_LIST_CCD_CASE_USER_ID);
        when(idamService.getUserIdentityData(map, GET_REFUND_LIST_CCD_CASE_USER_ID)).thenReturn(UserIdentityDataDto.userIdentityDataWith()
                                                                                                    .fullName("ccd-full-name")
                                                                                                    .emailId("j@mail.com")
                                                                                                    .build());
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
    void testRefundListForRefundSubmittedStatusExcludeCurrentUserTrue() throws Exception {
        when(refundsRepository.findByRefundStatusAndCreatedByIsNot(
            RefundStatus.SENTFORAPPROVAL,
            GET_REFUND_LIST_CCD_CASE_USER_ID
        ))
            .thenReturn(Optional.ofNullable(List.of(
                refundListSupplierForSubmittedStatus.get())));

        when(idamService.getUserId(map)).thenReturn(GET_REFUND_LIST_CCD_CASE_USER_ID);
        when(idamService.getUserIdentityData(map, GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)).thenReturn(
            UserIdentityDataDto.userIdentityDataWith().fullName("ccd-full-name-for-submitted-status").emailId("j@mail.com").build());

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
    void testRefundListForRefundSubmittedStatusExcludeCurrentUserFalse() throws Exception {
        when(refundsRepository.findByRefundStatus(
            RefundStatus.SENTFORAPPROVAL
        ))
            .thenReturn(Optional.ofNullable(List.of(
                refundListSupplierBasedOnCCDCaseNumber.get(),
                refundListSupplierForSubmittedStatus.get()
            )));

        when(idamService.getUserId(map)).thenReturn(GET_REFUND_LIST_CCD_CASE_USER_ID);

        when(idamService.getUserIdentityData(map, GET_REFUND_LIST_CCD_CASE_USER_ID)).thenReturn(
            UserIdentityDataDto.userIdentityDataWith().fullName("ccd-full-name").emailId("h@mail.com").build());
        when(idamService.getUserIdentityData(map, GET_REFUND_LIST_SUBMITTED_REFUND_CCD_CASE_USER_ID)).thenReturn(
            UserIdentityDataDto.userIdentityDataWith().fullName("ccd-full-name-for-submitted-status").emailId("h@mail.com").build()
            );

        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().code("RR001").name("duplicate payment").build()));

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
                        refundListSupplierBasedOnCCDCaseNumber.get())
                .status("AAA")
                .notes("BBB")
                .dateCreated(Timestamp.valueOf("2021-10-10 10:10:10"))
                .createdBy("CCC")
                .build();
        List<StatusHistory> statusHistories = new ArrayList<>();
        statusHistories.add(statusHistory);
        UserIdentityDataDto userIdentityDataDto = new UserIdentityDataDto();
        userIdentityDataDto.setFullName("Forename Surname");

        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refundListSupplierBasedOnCCDCaseNumber.get());
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
                        refundListSupplierBasedOnCCDCaseNumber.get())
                .status("AAA")
                .notes("BBB")
                .dateCreated(Timestamp.valueOf("2021-10-10 10:10:10"))
                .createdBy("CCC")
                .build();
        StatusHistory statusHistory2 = StatusHistory.statusHistoryWith()
                .id(2)
                .refund(
                        refundListSupplierBasedOnCCDCaseNumber.get())
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

        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refundListSupplierBasedOnCCDCaseNumber.get());
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
}
