package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconcilitationProviderFeeRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundFeeDto;
import uk.gov.hmcts.reform.refunds.model.RefundFees;

@Component
public class RefundFeeMapper {

    public RefundFees toRefundFee(RefundFeeDto refundFeeDtoo) {
        return RefundFees.refundFeesWith()
            .code(refundFeeDtoo.getCode())
            .feeId(refundFeeDtoo.getFeeId())
            .refundAmount(refundFeeDtoo.getRefundAmount())
            .volume(refundFeeDtoo.getVolume())
            .version(refundFeeDtoo.getVersion())
            .build();
    }

    public ReconcilitationProviderFeeRequest toRefundFeeForReconcilitationProvider(RefundFees refundFees){

        return ReconcilitationProviderFeeRequest.refundReconcilitationProviderFeeRequest()
            .code(refundFees.getCode())
            .version(Integer.parseInt(refundFees.getVersion()))
            .refundAmount(refundFees.getRefundAmount().toString())
            .build();
    }
}