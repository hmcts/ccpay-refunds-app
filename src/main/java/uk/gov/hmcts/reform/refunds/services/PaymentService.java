package uk.gov.hmcts.reform.refunds.services;

import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;

public interface PaymentService {
    PaymentGroupResponse fetchPaymentGroupResponse(MultiValueMap<String, String> headers, String paymentReference);
}
