package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;

@Component
public class RefundResponseMapper {

    @Autowired
    private RefundReasonRepository refundReasonRepository;

    public RefundDto getRefundListDto(Refund refund, UserIdentityDataDto userData) {
        return RefundDto
            .buildRefundListDtoWith()
            .ccdCaseNumber(refund.getCcdCaseNumber())
            .amount(refund.getAmount())
            .reason(getRefundReason(refund.getReason()))
            .refundStatus(refund.getRefundStatus())
            .refundReference(refund.getReference())
            .paymentReference(refund.getPaymentReference())
            .userFullName(userData.getFullName())
            .emailId(userData.getEmailId())
            .dateCreated(refund.getDateCreated().toString())
            .dateUpdated(refund.getDateUpdated().toString())
            .build();

    }

    private String getRefundReason(String rawReason){
        if(rawReason.startsWith("RR")) {
            return refundReasonRepository.findByCode(rawReason).get().getName();
        }
        return rawReason;
    }

}
