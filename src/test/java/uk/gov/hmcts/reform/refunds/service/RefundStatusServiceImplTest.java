package uk.gov.hmcts.reform.refunds.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.ContactDetailsDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.Notification;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.IdamService;
import uk.gov.hmcts.reform.refunds.services.NotificationService;
import uk.gov.hmcts.reform.refunds.services.RefundStatusServiceImpl;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.StatusHistoryUtil;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RefundStatusServiceImplTest {
    @Mock
    private RefundsRepository refundsRepository;

    @Mock
    private StatusHistoryUtil statusHistoryUtil;

    @Mock
    private NotificationService notificationService;

    @Mock
    private IdamService idamService;

    @Spy
    private RefundsUtil refundsUtil;

    @InjectMocks
    private RefundStatusServiceImpl refundStatusService;

    private AutoCloseable mocks;

    private static final String TEST_POSTAL_CODE = "AB12 3CD";
    private static final String TEST_CITY = "London";
    private static final String TEST_COUNTRY = "UK";
    private static final String TEST_COUNTY = "Greater London";
    private static final String TEST_ADDRESS_LINE = "123 Test Street";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_NOTIFICATION_TYPE = "EMAIL";

    private ContactDetailsDto contactDetailsDto;
    private Notification notification;

    private static List<StatusHistory> statusHistories;
    private static List<StatusHistory> reissueHistories;
    private static Refund rejectedRefund;
    private static ContactDetails contactDetails;

    @BeforeAll
    static void initAll() {
        // Static initialization if needed
        final StatusHistory rejectedHistory = new StatusHistory();
        rejectedRefund = new Refund();
        rejectedRefund.setReference("RF-1234-5678-9012-3456");
        rejectedRefund.setRefundStatus(RefundStatus.REJECTED);
        rejectedHistory.setRefund(rejectedRefund);
        rejectedHistory.setStatus(RefundStatus.REJECTED.getName());
        rejectedHistory.setNotes("Unable to apply refund to Card");
        statusHistories = Collections.singletonList(rejectedHistory);
        final StatusHistory reissuedHistory = new StatusHistory();
        reissuedHistory.setStatus(RefundStatus.REISSUED.getName());
        reissuedHistory.setNotes("Cloned from RF-ORIGINAL-REF-0001");
        reissueHistories = Collections.singletonList(reissuedHistory);
        contactDetails = ContactDetails.contactDetailsWith()
            .notificationType(TEST_NOTIFICATION_TYPE)
            .postalCode(TEST_POSTAL_CODE)
            .city(TEST_CITY)
            .country(TEST_COUNTRY)
            .county(TEST_COUNTY)
            .addressLine(TEST_ADDRESS_LINE)
            .email(TEST_EMAIL)
            .build();
    }

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        // Set arbitrary template IDs for testing
        setField(refundsUtil, "chequePoCashLetterTemplateId", "CHEQUE_PO_CASH_LETTER_ID");
        setField(refundsUtil, "chequePoCashEmailTemplateId", "CHEQUE_PO_CASH_EMAIL_ID");
        setField(refundsUtil, "cardPbaLetterTemplateId", "CARD_PBA_LETTER_ID");
        setField(refundsUtil, "cardPbaEmailTemplateId", "CARD_PBA_EMAIL_ID");
        setField(refundsUtil, "refundWhenContactedEmailTemplateId", "REFUND_WHEN_CONTACTED_EMAIL_ID");
        setField(refundsUtil, "refundWhenContactedLetterTemplateId", "REFUND_WHEN_CONTACTED_LETTER_ID");
        contactDetailsDto = mock(ContactDetailsDto.class);
        when(contactDetailsDto.getPostalCode()).thenReturn(TEST_POSTAL_CODE);
        when(contactDetailsDto.getCity()).thenReturn(TEST_CITY);
        when(contactDetailsDto.getCountry()).thenReturn(TEST_COUNTRY);
        when(contactDetailsDto.getCounty()).thenReturn(TEST_COUNTY);
        when(contactDetailsDto.getAddressLine()).thenReturn(TEST_ADDRESS_LINE);
        when(contactDetailsDto.getEmail()).thenReturn(TEST_EMAIL);
        notification = mock(Notification.class);
        when(notification.getContactDetails()).thenReturn(contactDetailsDto);
        when(notification.getNotificationType()).thenReturn(TEST_NOTIFICATION_TYPE);
    }

    private void setField(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void stubNotificationService() {
        when(notificationService.getNotificationDetails(any(), anyString())).thenReturn(notification);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testCardPaymentUpdateRefundStatusAccepted_OriginalRefundRejected() {
        Refund refund = new Refund();
        refund.setReference("RF-1234-5678-9012-3456");
        refund.setRefundStatus(RefundStatus.REJECTED);
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);
        when(statusHistoryUtil.isAClonedRefund(refund)).thenReturn(false);
        when(statusHistoryUtil.getOriginalRefundReference(refund)).thenReturn("RF-1234-5678-9012-3456");
        when(statusHistoryUtil.getOriginalNoteForRejected(refund)).thenReturn(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        RefundStatusUpdateRequest request = new RefundStatusUpdateRequest();
        request.setStatus(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED);
        request.setReason("Accepted");
        final MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        IdamTokenResponse idamTokenResponse = IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("token").build();
        when(idamService.getSecurityTokens()).thenReturn(idamTokenResponse);
        stubNotificationService();
        doNothing().when(notificationService).updateNotification(any(), any(), any(), anyString());
        ResponseEntity<?> response = refundStatusService.updateRefundStatus("RF-1234-5678-9012-3456", request, headers);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Refund status updated successfully", response.getBody());
        assertEquals(RefundsUtil.REFUND_WHEN_CONTACTED, refund.getRefundInstructionType());
        assertEquals(RefundStatus.ACCEPTED, refund.getRefundStatus());
        assertEquals(1, refund.getStatusHistories().size());
        assertEquals(RefundStatus.ACCEPTED.getName(), refund.getStatusHistories().get(0).getStatus());
        ArgumentCaptor<String> templateIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).updateNotification(any(), any(), any(), templateIdCaptor.capture());
        String capturedTemplateId = templateIdCaptor.getValue();
        assertEquals("REFUND_WHEN_CONTACTED_EMAIL_ID", capturedTemplateId);
    }

    @Test
    void testCardPaymentUpdateRefundStatusAccepted_ClonedRefundRejected() {
        Refund refund = new Refund();
        refund.setReference("RF-ORIGINAL-REF-0002");
        refund.setRefundStatus(RefundStatus.ACCEPTED);
        StatusHistory rejectedHistory = new StatusHistory();
        rejectedHistory.setStatus(RefundStatus.REJECTED.getName());
        rejectedHistory.setNotes(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        StatusHistory acceptedHistory = new StatusHistory();
        acceptedHistory.setStatus(RefundStatus.ACCEPTED.getName());
        acceptedHistory.setNotes(null);
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);
        when(statusHistoryUtil.isAClonedRefund(refund)).thenReturn(true);
        when(statusHistoryUtil.getOriginalRefundReference(refund)).thenReturn("RF-ORIGINAL-REF-0002");
        when(statusHistoryUtil.getOriginalNoteForRejected(refund)).thenReturn(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        RefundStatusUpdateRequest request = new RefundStatusUpdateRequest();
        request.setStatus(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED);
        request.setReason(null);
        IdamTokenResponse idamTokenResponse = IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("token").build();
        when(idamService.getSecurityTokens()).thenReturn(idamTokenResponse);
        stubNotificationService();
        refund.setContactDetails(contactDetails);
        doNothing().when(notificationService).updateNotification(any(), any(), any(), anyString());
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        ResponseEntity<?> response = refundStatusService.updateRefundStatus("RF-ORIGINAL-REF-0002", request, headers);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Refund status updated successfully", response.getBody());
        assertEquals(RefundsUtil.REFUND_WHEN_CONTACTED, refund.getRefundInstructionType());
        ArgumentCaptor<String> templateIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).updateNotification(any(), any(), any(), templateIdCaptor.capture());
        assertEquals("REFUND_WHEN_CONTACTED_EMAIL_ID", templateIdCaptor.getValue());
    }

    @Test
    void testChequePaymentUpdateRefundStatusAccepted_BulkScanRefundApproved() {
        Refund refund = new Refund();
        refund.setReference("RF-ORIGINAL-REF-0003");
        refund.setRefundStatus(RefundStatus.APPROVED);
        refund.setRefundInstructionType(RefundsUtil.REFUND_WHEN_CONTACTED);
        StatusHistory rejectedHistory = new StatusHistory();
        rejectedHistory.setStatus(RefundStatus.APPROVED.getName());
        rejectedHistory.setNotes("Sent to middle office for processing");
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);
        RefundStatusUpdateRequest request = new RefundStatusUpdateRequest();
        request.setStatus(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED);
        request.setReason(null);
        final MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        IdamTokenResponse idamTokenResponse = IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("token").build();
        when(idamService.getSecurityTokens()).thenReturn(idamTokenResponse);
        stubNotificationService();
        refund.setContactDetails(contactDetails);
        doNothing().when(notificationService).updateNotification(any(), any(), any(), anyString());
        ResponseEntity<?> response = refundStatusService.updateRefundStatus("RF-ORIGINAL-REF-0003", request, headers);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Refund status updated successfully", response.getBody());
        assertEquals(RefundsUtil.REFUND_WHEN_CONTACTED, refund.getRefundInstructionType());
        ArgumentCaptor<String> templateIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).updateNotification(any(), any(), any(), templateIdCaptor.capture());
        assertEquals("CHEQUE_PO_CASH_EMAIL_ID", templateIdCaptor.getValue());
    }

    @Test
    void testUpdateRefundStatusExpired_setsStatusNoTemplateSet() throws Exception {
        Refund refund = new Refund();
        refund.setReference("RF-EXPIRED-REF-0001");
        refund.setRefundStatus(RefundStatus.EXPIRED);
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);
        RefundStatusUpdateRequest request = new RefundStatusUpdateRequest();
        request.setStatus(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED);
        request.setReason("Expired");
        final MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        doNothing().when(notificationService).updateNotification(any(), any(), any(), anyString());
        ResponseEntity<?> response = refundStatusService.updateRefundStatus("RF-EXPIRED-REF-0001", request, headers);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Refund status updated successfully", response.getBody());
        assertEquals(RefundStatus.EXPIRED, refund.getRefundStatus());
    }

    @Test
    void testGetOriginalRefund_cloned_returnsExtractedReference() {
        Refund refund = new Refund();
        refund.setReference("RF-CLONED-REF-0001");
        Refund originalRefund = new Refund();
        originalRefund.setReference("RF-1234-5678-9012-3456");
        when(statusHistoryUtil.isAClonedRefund(refund)).thenReturn(true);
        when(statusHistoryUtil.getOriginalRefundReference(refund)).thenReturn(originalRefund.getReference());
        String result = statusHistoryUtil.getOriginalRefundReference(refund);
        assertEquals("RF-1234-5678-9012-3456", result);
    }

    @Test
    void testGetOriginalRefund_cloned_noReissued_returnsNull() {
        Refund refund = new Refund();
        refund.setReference("RF-CLONED-REF-0002");
        when(statusHistoryUtil.isAClonedRefund(refund)).thenReturn(true);
        String result = statusHistoryUtil.getOriginalRefundReference(refund);
        assertEquals(null, result);
    }
}


