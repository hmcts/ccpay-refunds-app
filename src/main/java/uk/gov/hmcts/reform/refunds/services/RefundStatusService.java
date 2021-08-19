package uk.gov.hmcts.reform.refunds.services;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dto.RefundStatusUpdateRequest;

public interface RefundStatusService {

    ResponseEntity updateRefundStatus(String reference, RefundStatusUpdateRequest refundStatusUpdateRequest, MultiValueMap<String, String> headers);
}
