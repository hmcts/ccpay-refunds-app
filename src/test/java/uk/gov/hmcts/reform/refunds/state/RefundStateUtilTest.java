package uk.gov.hmcts.reform.refunds.state;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RefundStateUtilTest extends StateUtil {

    @Test
    void nextStateForSubmitOrSentForApproval() {

        RefundState refundState = RefundState.SENTFORAPPROVAL;
        assertEquals(RefundState.APPROVED, refundState.nextState(RefundEvent.APPROVE));
        assertEquals(RefundState.REJECTED, refundState.nextState(RefundEvent.REJECT));
        assertEquals(RefundState.NEEDMOREINFO, refundState.nextState(RefundEvent.UPDATEREQUIRED));
    }

    @Test
    void nextStateForApprove() {

        RefundState refundState = RefundState.APPROVED;
        assertEquals(RefundState.REJECTED, refundState.nextState(RefundEvent.REJECT));
        assertEquals(RefundState.ACCEPTED, refundState.nextState(RefundEvent.ACCEPT));
    }

    @Test
    void nextStateForNeedMoreInfo() {

        RefundState refundState = RefundState.NEEDMOREINFO;
        assertEquals(RefundState.SENTFORAPPROVAL, refundState.nextState(RefundEvent.SUBMIT));
        assertEquals(RefundState.CANCELLED, refundState.nextState(RefundEvent.CANCEL));
    }

    @Test
    void nextStateForAccept() {

        RefundState refundState = RefundState.ACCEPTED;
        assertEquals(RefundState.ACCEPTED, refundState.nextState(RefundEvent.SUBMIT));
    }

    @Test
    void nextStateForReject() {

        RefundState refundState = RefundState.REJECTED;
        assertEquals(RefundState.REJECTED, refundState.nextState(RefundEvent.SUBMIT));
    }


    @Test
    void returnNullOnInvalidState() {
        RefundState refundState = getRefundState("invalid state");
        assertNull(refundState);
    }
}
