package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFailureDto;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

@Component
public class PaymentFailureResponseMapper {

    public PaymentFailureDto getPaymentFailureDto(Refund refund) {

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.UK);

        return PaymentFailureDto.buildPaymentFailureDtoWith()
            .refundReference(refund.getReference())
            .paymentReference(refund.getPaymentReference())
            .amount(refund.getAmount())
            .refundDate(dateFormat.format(refund.getDateUpdated()))
            .build();
    }
}
