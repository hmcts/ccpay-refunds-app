package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFailureDto;
import uk.gov.hmcts.reform.refunds.model.Refund;

@Component
public class PaymentFailureResponseMapper {

    public PaymentFailureDto getPaymentFailureDto(Refund refund) {
        return PaymentFailureDto.buildPaymentFailureDtoWith()
            .refundReference(refund.getReference())
            .paymentReference(refund.getPaymentReference())
            .amount(refund.getAmount())
            .refundDate(refund.getDateUpdated().toString())
            .build();
    }
}
