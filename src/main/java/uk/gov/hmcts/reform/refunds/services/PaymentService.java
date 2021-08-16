package uk.gov.hmcts.reform.refunds.services;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;

public interface PaymentService {
    ResponseEntity<PaymentGroupResponse> fetchDetailFromPayment(MultiValueMap<String, String> headers, String paymentReference);
}
