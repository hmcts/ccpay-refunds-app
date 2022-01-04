package uk.gov.hmcts.reform.refunds.services;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;

public interface RefundNotificationService {
    ResponseEntity resendRefundNotification(Boolean resendToNewContactDetail,ResendNotificationRequest resendNotificationRequest, MultiValueMap<String, String> headers);
}
