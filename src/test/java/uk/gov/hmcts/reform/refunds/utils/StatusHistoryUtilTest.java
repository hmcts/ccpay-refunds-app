package uk.gov.hmcts.reform.refunds.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class StatusHistoryUtilTest {
    private StatusHistoryUtil statusHistoryUtil;
    private StatusHistoryRepository repository;

    private Refund refund;

    @BeforeEach
    void setUp() {
        statusHistoryUtil = spy(StatusHistoryUtil.class);
        repository = spy(StatusHistoryRepository.class);

        refund = new Refund();
        refund.setReference("RF-1111-2222-3333-4444");
    }

    @Test
    void testIsAClonedRefund_trueWhenReissuedPresent() {
        StatusHistory reissued = new StatusHistory();
        reissued.setStatus(RefundStatus.REISSUED.getName());
        List<StatusHistory> histories = Collections.singletonList(reissued);
        doReturn(histories).when(repository).findByRefundOrderByDateCreatedDesc(refund);
        assertTrue(statusHistoryUtil.isAClonedRefund(refund));
    }

    @Test
    void testIsAClonedRefund_falseWhenNoReissued() {
        StatusHistory accepted = new StatusHistory();
        accepted.setStatus(RefundStatus.ACCEPTED.getName());
        List<StatusHistory> histories = Collections.singletonList(accepted);
        doReturn(histories).when(repository).findByRefundOrderByDateCreatedDesc(refund);
        assertFalse(statusHistoryUtil.isAClonedRefund(refund));
    }

    @Test
    void testGetOriginalRefundReference_clonedRefund() {
        StatusHistory reissued = new StatusHistory();
        reissued.setStatus(RefundStatus.REISSUED.getName());
        reissued.setNotes("Cloned from RF-2222-3333-4444-5555");
        List<StatusHistory> histories = Collections.singletonList(reissued);
        doReturn(histories).when(repository).findByRefundOrderByDateCreatedDesc(refund);
        doReturn(true).when(statusHistoryUtil).isAClonedRefund(refund);
        String result = statusHistoryUtil.getOriginalRefundReference(refund);
        assertEquals("RF-2222-3333-4444-5555", result);
    }

    @Test
    void testGetOriginalRefundReference_nonClonedRefund() {
        doReturn(false).when(statusHistoryUtil).isAClonedRefund(refund);
        String result = statusHistoryUtil.getOriginalRefundReference(refund);
        assertEquals("RF-1111-2222-3333-4444", result);
    }

    @Test
    void testExtractRefundReference_valid() {
        String input = "Some notes RF-1746-5507-4452-0488 more text";
        String result = statusHistoryUtil.extractRefundReference(input);
        assertEquals("RF-1746-5507-4452-0488", result);
    }

    @Test
    void testExtractRefundReference_invalid() {
        String input = "No valid reference here";
        String result = statusHistoryUtil.extractRefundReference(input);
        assertNull(result);
    }
}

