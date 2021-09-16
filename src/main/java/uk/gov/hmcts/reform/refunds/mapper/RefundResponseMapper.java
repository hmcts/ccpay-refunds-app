package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.model.Refund;

@Component
public class RefundResponseMapper {


    public RefundDto getRefundListDto(Refund refund, UserIdentityDataDto userData,String reason) {
        return RefundDto
            .buildRefundListDtoWith()
            .ccdCaseNumber(refund.getCcdCaseNumber())
            .amount(refund.getAmount())
            .reason(reason)
            .refundStatus(refund.getRefundStatus())
            .refundReference(refund.getReference())
            .paymentReference(refund.getPaymentReference())
            .userFullName(userData == null ? "" : userData.getFullName())
            .emailId(userData == null ? "" : userData.getEmailId())
            .dateCreated(refund.getDateCreated().toString())
            .dateUpdated(refund.getDateUpdated().toString())
            .build();

    }


}
