package uk.gov.hmcts.reform.refunds.services;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;

public interface NotificationService {

    ResponseEntity<String> postEmailNotificationData(MultiValueMap<String, String> headers,
                                                     RefundNotificationEmailRequest refundNotificationEmailRequest);

    ResponseEntity<String> postLetterNotificationData(MultiValueMap<String, String> headers,
                                                      RefundNotificationLetterRequest refundNotificationLetterRequest);
}
