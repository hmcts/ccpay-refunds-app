package uk.gov.hmcts.reform.refunds.utils;

import uk.gov.hmcts.reform.refunds.exceptions.ActionNotFoundException;
import uk.gov.hmcts.reform.refunds.state.RefundState;

public class StateUtil {

    protected RefundState getRefundState(String status) throws ActionNotFoundException{
        switch (status) {
            case "sent for approval":
                return uk.gov.hmcts.reform.refunds.state.RefundState.SENTFORAPPROVAL;
            case "sent to middle office":
                return uk.gov.hmcts.reform.refunds.state.RefundState.SENTTOMIDDLEOFFICE;
            case "sent back":
                return uk.gov.hmcts.reform.refunds.state.RefundState.NEEDMOREINFO;
            case "accepted":
            case "rejected":
                throw new ActionNotFoundException("No actions to proceed further");
        }
        return null;
    }
}
