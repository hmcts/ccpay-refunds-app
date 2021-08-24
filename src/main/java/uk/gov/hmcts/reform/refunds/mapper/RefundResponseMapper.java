package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.model.Refund;

@Component
public class RefundResponseMapper {

    public RefundDto getRefundListDto(Refund refund, String userFullname) {
        return RefundDto
            .buildRefundListDtoWith()
            .ccdCaseNumber(refund.getCcdCaseNumber())
            .amount(refund.getAmount())
            .reason(refund.getReason())
            .refundStatus(refund.getRefundStatus())
            .refundReference(refund.getReference())
            .paymentReference(refund.getPaymentReference())
            .userFullName(userFullname)
            .dateCreated(refund.getDateCreated().toString())
            .dateUpdated(refund.getDateUpdated().toString())
            .build();

    }
}
