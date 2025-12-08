package uk.gov.hmcts.reform.refunds.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.ContactDetailsDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.Notification;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;
import uk.gov.hmcts.reform.refunds.services.IdamService;
import uk.gov.hmcts.reform.refunds.services.NotificationService;
import uk.gov.hmcts.reform.refunds.services.RefundStatusServiceImpl;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RefundStatusServiceImplTest {
    @Mock
    private RefundsRepository refundsRepository;
    @Mock
    private StatusHistoryRepository statusHistoryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private IdamService idamService;
    @Mock
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

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
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
    void testUpdateRefundStatusAccepted_OriginalRefund() {
        Refund refund = new Refund();
        refund.setReference("RF-1234-5678-9012-3456");
        refund.setRefundStatus(RefundStatus.REJECTED);
        // isAClonedRefund = false
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);
        when(statusHistoryRepository.findByRefundOrderByDateCreatedDesc(any())).thenReturn(Collections.emptyList());
        RefundStatusUpdateRequest request = new RefundStatusUpdateRequest();
        request.setStatus(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED);
        request.setReason("Accepted");
        final MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        IdamTokenResponse idamTokenResponse = IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("token").build();
        when(idamService.getSecurityTokens()).thenReturn(idamTokenResponse);
        stubNotificationService();
        when(refundsUtil.getTemplate(any(), anyString())).thenReturn("templateId");
        doNothing().when(notificationService).updateNotification(any(), any(), any(), anyString());
        ResponseEntity<?> response = refundStatusService.updateRefundStatus("RF-1234-5678-9012-3456", request, headers);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Refund status updated successfully", response.getBody());
        // Should NOT set RefundInstructionType
        assertEquals(null, refund.getRefundInstructionType());
    }

    @Test
    void testUpdateRefundStatusAccepted_LastAcceptedStatusTriggersRefundWhenContacted() {
        Refund refund = new Refund();
        refund.setReference("RF-ORIGINAL-REF-0002");
        refund.setRefundStatus(RefundStatus.REJECTED);
        // isAClonedRefund = false
        StatusHistory accepted1 = new StatusHistory();
        accepted1.setStatus(RefundStatus.ACCEPTED.getName());
        accepted1.setNotes("Some other reason");
        StatusHistory accepted2 = new StatusHistory();
        accepted2.setStatus(RefundStatus.ACCEPTED.getName());
        accepted2.setNotes("Another valid reason"); // last ACCEPTED, not REFUND_WHEN_CONTACTED_REJECT_REASON
        when(statusHistoryRepository.findByRefundOrderByDateCreatedDesc(refund)).thenReturn(java.util.Arrays.asList(accepted1, accepted2));
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);
        RefundStatusUpdateRequest request = new RefundStatusUpdateRequest();
        request.setStatus(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED);
        request.setReason("Accepted");
        final MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        IdamTokenResponse idamTokenResponse = IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("token").build();
        when(idamService.getSecurityTokens()).thenReturn(idamTokenResponse);
        stubNotificationService();
        when(refundsUtil.getTemplate(any(), anyString())).thenReturn("templateId");
        doNothing().when(notificationService).updateNotification(any(), any(), any(), anyString());
        ResponseEntity<?> response = refundStatusService.updateRefundStatus("RF-ORIGINAL-REF-0002", request, headers);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Refund status updated successfully", response.getBody());
        assertEquals(RefundsUtil.REFUND_WHEN_CONTACTED, refund.getRefundInstructionType());
    }

    @Test
    void testUpdateRefundStatusAccepted_ClonedRefund() {
        Refund refund = new Refund();
        refund.setReference("RF-CLONED-REF-0001");
        refund.setRefundStatus(RefundStatus.REJECTED);
        // isAClonedRefund = true
        StatusHistory reissuedHistory = new StatusHistory();
        reissuedHistory.setStatus(RefundStatus.REISSUED.getName());
        reissuedHistory.setNotes("Cloned from RF-ORIGINAL-REF-0001");
        when(statusHistoryRepository.findByRefundOrderByDateCreatedDesc(refund)).thenReturn(Collections.singletonList(reissuedHistory));
        when(refundsRepository.findByReferenceOrThrow("RF-CLONED-REF-0001")).thenReturn(refund);
        // Mock original refund and its note
        Refund originalRefund = new Refund();
        originalRefund.setReference("RF-ORIGINAL-REF-0001");
        when(refundsRepository.findByReferenceOrThrow("RF-ORIGINAL-REF-0001")).thenReturn(originalRefund);
        StatusHistory rejectedHistory = new StatusHistory();
        rejectedHistory.setStatus(RefundStatus.REJECTED.getName());
        rejectedHistory.setNotes("Original rejected reason");
        when(statusHistoryRepository.findByRefundOrderByDateCreatedDesc(originalRefund)).thenReturn(Collections.singletonList(rejectedHistory));
        RefundStatusUpdateRequest request = new RefundStatusUpdateRequest();
        request.setStatus(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.ACCEPTED);
        request.setReason(null); // Should be set from original refund's rejected note
        final MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        IdamTokenResponse idamTokenResponse = IdamTokenResponse.idamFullNameRetrivalResponseWith().accessToken("token").build();
        when(idamService.getSecurityTokens()).thenReturn(idamTokenResponse);
        stubNotificationService();
        when(refundsUtil.getTemplate(any(), anyString())).thenReturn("templateId");
        doNothing().when(notificationService).updateNotification(any(), any(), any(), anyString());
        ResponseEntity<?> response = refundStatusService.updateRefundStatus("RF-CLONED-REF-0001", request, headers);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Refund status updated successfully", response.getBody());
        assertEquals(RefundsUtil.REFUND_WHEN_CONTACTED, refund.getRefundInstructionType());
    }

    @Test
    void testUpdateRefundStatusExpired() {
        Refund refund = new Refund();
        refund.setReference("RF-1234-5678-9012-3456");
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);
        RefundStatusUpdateRequest request = new RefundStatusUpdateRequest();
        request.setStatus(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.EXPIRED);
        request.setReason("Expired");
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        ResponseEntity<?> response = refundStatusService.updateRefundStatus("RF-1234-5678-9012-3456", request, headers);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Refund status updated successfully", response.getBody());
    }

    @Test
    void testUpdateRefundStatusRejectedWithRefundWhenContacted() {
        Refund refund = new Refund();
        refund.setReference("RF-1234-5678-9012-3456");
        when(refundsRepository.findByReferenceOrThrow(anyString())).thenReturn(refund);
        RefundStatusUpdateRequest request = new RefundStatusUpdateRequest();
        request.setStatus(uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatus.REJECTED);
        request.setReason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        ResponseEntity<?> response = refundStatusService.updateRefundStatus("RF-1234-5678-9012-3456", request, headers);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Refund status updated successfully", response.getBody());
    }

    @Test
    void testExtractRefundReference() {
        String notes = "Some notes RF-1746-5507-4452-0488 more text";
        String ref = refundStatusService.extractRefundReference(notes);
        assertEquals("RF-1746-5507-4452-0488", ref);
    }
}
