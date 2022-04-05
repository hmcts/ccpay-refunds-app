package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundFeeDto;
import uk.gov.hmcts.reform.refunds.model.RefundFees;

@Component
public class RefundFeeMapper {

    public RefundFees toRefundFee(RefundFeeDto refundFeeDto) {
        return RefundFees.refundFeesWith()
            .code(refundFeeDto.getCode())
            .feeId(refundFeeDto.getFeeId())
            .refundAmount(refundFeeDto.getRefundAmount())
            .volume(refundFeeDto.getVolume())
            .version(refundFeeDto.getVersion())
            .build();
    }

    public RefundFeeDto toRefundFeeDto(RefundFees refundFees) {
        return RefundFeeDto.refundFeeRequestWith()
            .code(refundFees.getCode())
            .feeId(refundFees.getFeeId())
            .refundAmount(refundFees.getRefundAmount())
            .volume(refundFees.getVolume())
            .version(refundFees.getVersion())
            .build();
    }
}
