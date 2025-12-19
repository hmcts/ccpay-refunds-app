package uk.gov.hmcts.reform.refunds.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class StatusHistoryUtilTest {
    private StatusHistoryUtil statusHistoryUtil;
    private StatusHistoryRepository repository;
    private RefundsRepository refundsRepository;

    private Refund refund;

    @BeforeEach
    void setUp() throws Exception {
        repository = spy(StatusHistoryRepository.class);
        refundsRepository = spy(RefundsRepository.class);
        statusHistoryUtil = spy(StatusHistoryUtil.class);
        // Inject the repositories into the util
        java.lang.reflect.Field field = StatusHistoryUtil.class.getDeclaredField("statusHistoryRepository");
        field.setAccessible(true);
        field.set(statusHistoryUtil, repository);

        java.lang.reflect.Field rfield = StatusHistoryUtil.class.getDeclaredField("refundsRepository");
        rfield.setAccessible(true);
        rfield.set(statusHistoryUtil, refundsRepository);

        refund = new Refund();
        refund.setReference("RF-1111-2222-3333-4444");
        refund.setPaymentReference("RC-1111-2222-3333-4444");
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

    // Helpers
    private static Refund refundWithReissuedNote(String refundRef, String origRef) {
        Refund r = new Refund();
        r.setReference(refundRef);
        StatusHistory h = new StatusHistory();
        h.setStatus(RefundStatus.REISSUED.getName());
        h.setNotes("Some text - re-issue of original refund " + origRef);
        r.setStatusHistories(Collections.singletonList(h));
        return r;
    }

    private static Refund refundWithoutMatchingNote(String refundRef, String otherOrig) {
        Refund r = new Refund();
        r.setReference(refundRef);
        StatusHistory h = new StatusHistory();
        h.setStatus(RefundStatus.REISSUED.getName());
        h.setNotes("Some text - re-issue of original refund " + otherOrig);
        r.setStatusHistories(Collections.singletonList(h));
        return r;
    }

    @Test
    void testGetReissueLabel_firstReissue_expiredCount1() {
        String orig = "RF-AAAA-BBBB-CCCC-DDDD";
        doReturn(orig).when(statusHistoryUtil).getOriginalRefundReference(refund);
        // No matching prior reissues
        doReturn(Optional.of(Collections.emptyList()))
            //when(refundsRepository).findByPaymentReference(refund.getPaymentReference());
            .when(refundsRepository).findByPaymentReference("RC-1111-2222-3333-4444");

        String label = statusHistoryUtil.getReissueLabel(refund);
        assertEquals("1st re-issue of original refund " + orig, label);
    }

    @Test
    void testGetReissueLabel_secondReissue_expiredCount2() {
        String orig = "RF-AAAA-BBBB-CCCC-DDDD";
        doReturn(orig).when(statusHistoryUtil).getOriginalRefundReference(refund);

        Refund prior1 = refundWithReissuedNote("RF-PRIOR-0001-0001-0001", orig);
        // Include unrelated that should not count
        Refund unrelated = refundWithoutMatchingNote("RF-PRIOR-XXXX-XXXX-XXXX", "RF-OTHER-1111-1111-1111");

        doReturn(Optional.of(Arrays.asList(prior1, unrelated)))
            .when(refundsRepository).findByPaymentReference(refund.getPaymentReference());

        String label = statusHistoryUtil.getReissueLabel(refund);
        assertEquals("2nd re-issue of original refund " + orig, label);
    }

    @Test
    void testGetReissueLabel_thirdReissue_expiredCount3() {
        String orig = "RF-AAAA-BBBB-CCCC-DDDD";
        doReturn(orig).when(statusHistoryUtil).getOriginalRefundReference(refund);

        Refund prior1 = refundWithReissuedNote("RF-PRIOR-0001-0001-0001", orig);
        Refund prior2 = refundWithReissuedNote("RF-PRIOR-0002-0002-0002", orig);

        doReturn(Optional.of(Arrays.asList(prior1, prior2)))
            .when(refundsRepository).findByPaymentReference(refund.getPaymentReference());

        String label = statusHistoryUtil.getReissueLabel(refund);
        assertEquals("3rd re-issue of original refund " + orig, label);
    }

    @Test
    void testGetReissueLabel_fourthReissue_expiredCount4() {
        String orig = "RF-AAAA-BBBB-CCCC-DDDD";
        doReturn(orig).when(statusHistoryUtil).getOriginalRefundReference(refund);

        Refund prior1 = refundWithReissuedNote("RF-PRIOR-0001-0001-0001", orig);
        Refund prior2 = refundWithReissuedNote("RF-PRIOR-0002-0002-0002", orig);
        Refund prior3 = refundWithReissuedNote("RF-PRIOR-0003-0003-0003", orig);

        doReturn(Optional.of(Arrays.asList(prior1, prior2, prior3)))
            .when(refundsRepository).findByPaymentReference(refund.getPaymentReference());

        String label = statusHistoryUtil.getReissueLabel(refund);
        assertEquals("4th re-issue of original refund " + orig, label);
    }

    @Test
    void testGetReissueLabel_nonCloned_usesOwnReference_expiredCount1() {
        // Non-cloned refund path: util should fall back to refund.getReference()
        doReturn(false).when(statusHistoryUtil).isAClonedRefund(refund);
        // No REISSUED history on this refund
        doReturn(Collections.emptyList()).when(repository).findByRefundOrderByDateCreatedDesc(refund);
        // No prior related reissues for this payment
        doReturn(Optional.of(Collections.emptyList()))
            .when(refundsRepository).findByPaymentReference(refund.getPaymentReference());

        String label = statusHistoryUtil.getReissueLabel(refund);
        assertEquals("1st re-issue of original refund " + refund.getReference(), label);
    }

    @Test
    void testGetReissueLabel_mixedOriginals_countsIndependently() {
        // Refund under test for origA
        Refund refundA = new Refund();
        refundA.setReference("RF-A-NEW-0000-0000-0001");
        refundA.setPaymentReference("RC-MIXED-REF-0001");
        // Refund under test for origB
        Refund refundB = new Refund();
        refundB.setReference("RF-B-NEW-0000-0000-0001");
        refundB.setPaymentReference("RC-MIXED-REF-0001");

        // Make util return the appropriate originals per refund
        String origA = "RF-AAAA-BBBB-CCCC-DDDD";
        doReturn(origA).when(statusHistoryUtil).getOriginalRefundReference(refundA);

        String origB = "RF-EEEE-FFFF-GGGG-HHHH";
        doReturn(origB).when(statusHistoryUtil).getOriginalRefundReference(refundB);

        // Build prior refunds: 2 matching origA, 4 matching origB, plus an unrelated one
        Refund a1 = refundWithReissuedNote("RF-A-PRIOR-0001", origA);
        Refund a2 = refundWithReissuedNote("RF-A-PRIOR-0002", origA);

        Refund b1 = refundWithReissuedNote("RF-B-PRIOR-0001", origB);
        Refund b2 = refundWithReissuedNote("RF-B-PRIOR-0002", origB);
        Refund b3 = refundWithReissuedNote("RF-B-PRIOR-0003", origB);
        Refund b4 = refundWithReissuedNote("RF-B-PRIOR-0004", origB);

        Refund unrelated = refundWithoutMatchingNote("RF-U-PRIOR-XXXX", "RF-OTHER-XXXX-XXXX-XXXX");

        List<Refund> mixed = Arrays.asList(a1, a2, b1, b2, b3, b4, unrelated);

        // Same payment reference used for both refunds, repository returns mixed set
        doReturn(Optional.of(mixed)).when(refundsRepository).findByPaymentReference("RC-MIXED-REF-0001");

        // Assert for refundA: prior count 2 => current should be 3rd re-issue
        String labelA = statusHistoryUtil.getReissueLabel(refundA);
        assertEquals("3rd re-issue of original refund " + origA, labelA);

        // Assert for refundB: prior count 4 => current should be 5th re-issue
        String labelB = statusHistoryUtil.getReissueLabel(refundB);
        assertEquals("5th re-issue of original refund " + origB, labelB);
    }
}
