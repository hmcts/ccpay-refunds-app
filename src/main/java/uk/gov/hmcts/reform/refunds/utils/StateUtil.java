package uk.gov.hmcts.reform.refunds.utils;

import uk.gov.hmcts.reform.refunds.exceptions.ActionNotAllowedException;
import uk.gov.hmcts.reform.refunds.state.RefundState;

import static uk.gov.hmcts.reform.refunds.state.RefundState.APPROVED;
import static uk.gov.hmcts.reform.refunds.state.RefundState.NEEDMOREINFO;
import static uk.gov.hmcts.reform.refunds.state.RefundState.SENTFORAPPROVAL;

public class StateUtil {

    protected RefundState getRefundState(String status) {
        switch (status) {
            case "sent for approval":
                return SENTFORAPPROVAL;
            case "approved":
                return APPROVED;
            case "update required":
                return NEEDMOREINFO;
            case "accepted":
            case "rejected":
                throw new ActionNotAllowedException("Action not allowed to proceed");
            default:
                return null;
        }
    }
}
