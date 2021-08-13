package uk.gov.hmcts.reform.refunds.services;

import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.PaymentFeeDetailsDto;

public interface PaymentService {
    PaymentFeeDetailsDto getPaymentData(MultiValueMap<String, String> headers, String paymentReference);
}
