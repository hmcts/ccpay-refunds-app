package uk.gov.hmcts.reform.refunds.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.exceptions.NotificationDeleteException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundsDeletionServiceTest {

    private static final String CCD_CASE_NUMBER = "1747738470383168";

    @Mock
    private RefundsRepository refundsRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RefundsServiceImpl refundsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void deleteRefundsByCcdCaseNumber_shouldDeleteNotificationsThenRefunds() {
        Refund refund = buildRefund("RF-1756-8279-1771-7688");
        when(refundsRepository.findByCcdCaseNumber(CCD_CASE_NUMBER))
            .thenReturn(Optional.of(List.of(refund)));

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        refundsService.deleteRefundsByCcdCaseNumber(headers, CCD_CASE_NUMBER);

        verify(notificationService).deleteNotification(headers, "RF-1756-8279-1771-7688");
        verify(refundsRepository).deleteAll(List.of(refund));
    }

    @Test
    void deleteRefundsByCcdCaseNumber_shouldReturnWhenNoRefundsFound() {
        when(refundsRepository.findByCcdCaseNumber(CCD_CASE_NUMBER))
            .thenReturn(Optional.of(Collections.emptyList()));

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        refundsService.deleteRefundsByCcdCaseNumber(headers, CCD_CASE_NUMBER);

        verify(notificationService, never()).deleteNotification(any(), anyString());
        verify(refundsRepository, never()).deleteAll(any());
    }

    @Test
    void deleteRefundsByCcdCaseNumber_shouldNotDeleteRefundsWhenNotificationDeleteFails() {
        Refund refund = buildRefund("RF-1756-8279-1771-7688");
        when(refundsRepository.findByCcdCaseNumber(CCD_CASE_NUMBER))
            .thenReturn(Optional.of(List.of(refund)));
        doThrow(new NotificationDeleteException("failed"))
            .when(notificationService).deleteNotification(any(), eq("RF-1756-8279-1771-7688"));

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        assertThrows(NotificationDeleteException.class,
            () -> refundsService.deleteRefundsByCcdCaseNumber(headers, CCD_CASE_NUMBER));

        verify(refundsRepository, never()).deleteAll(any());
    }

    private Refund buildRefund(String reference) {
        return Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.TEN)
            .ccdCaseNumber(CCD_CASE_NUMBER)
            .reference(reference)
            .paymentReference("RC-1111-2222-3333-4444")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .build();
    }
}
