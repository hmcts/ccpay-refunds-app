package uk.gov.hmcts.reform.refunds.services;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;

public interface RefundReviewService {
    ResponseEntity<String> reviewRefund(MultiValueMap<String, String> headers, String reference,
                                        RefundEvent refundEvent, RefundReviewRequest refundReviewRequest);

    ResponseEntity<String> cancelRefunds(MultiValueMap<String, String> headers, String paymentReference);
}
