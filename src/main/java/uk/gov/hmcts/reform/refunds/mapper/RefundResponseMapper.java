package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDto;
import uk.gov.hmcts.reform.refunds.model.Refund;

@Component
public class RefundResponseMapper {

    public RefundListDto getRefundListDto(Refund refund, String userFullname) {
        return RefundListDto
            .buildRefundListDtoWith()
            .ccdCaseNumber(refund.getCcdCaseNumber())
            .amount(refund.getAmount())
            .reason(refund.getReason())
            .refundReference(refund.getReference())
            .paymentReference(refund.getPaymentReference())
            .userFullName(userFullname)
            .dateCreated(refund.getDateCreated().toString())
            .dateUpdated(refund.getDateUpdated().toString())
            .build();

    }
}
