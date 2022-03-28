package uk.gov.hmcts.reform.refunds.utils;

import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundFees;

import java.util.ArrayList;
import java.util.List;

public class RefundFeeListMapper {

    public List<RefundFees> toRefundFeesList(Refund refund) {

        List<RefundFees> refundFeesList = new ArrayList<>();

        for (int i = 0; i <= refund.getRefundFees().size(); i++) {
            refundFeesList.add(refund.getRefundFees().get(i));
        }


        return refundFeesList;
    }
}
