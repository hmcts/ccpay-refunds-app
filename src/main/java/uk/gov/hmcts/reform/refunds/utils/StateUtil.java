package uk.gov.hmcts.reform.refunds.utils;

import uk.gov.hmcts.reform.refunds.exceptions.ActionNotFoundException;
import uk.gov.hmcts.reform.refunds.state.RefundState;

import static uk.gov.hmcts.reform.refunds.state.RefundState.NEEDMOREINFO;
import static uk.gov.hmcts.reform.refunds.state.RefundState.SENTFORAPPROVAL;
import static uk.gov.hmcts.reform.refunds.state.RefundState.SENTTOMIDDLEOFFICE;

public class StateUtil {

    protected RefundState getRefundState(String status) {
        switch (status) {
            case "sent for approval":
                return SENTFORAPPROVAL;
            case "sent to middle office":
                return SENTTOMIDDLEOFFICE;
            case "sent back":
                return NEEDMOREINFO;
            case "accepted":
            case "rejected":
                throw new ActionNotFoundException("No actions to proceed further");
            default:
                return null;
        }
    }
}
