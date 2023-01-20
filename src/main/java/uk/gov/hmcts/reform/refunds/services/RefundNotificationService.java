package uk.gov.hmcts.reform.refunds.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.DocPreviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.NotificationTemplatePreviewResponse;

public interface RefundNotificationService {
    ResponseEntity<String> resendRefundNotification(ResendNotificationRequest resendNotificationRequest, MultiValueMap<String, String> headers);

    void processFailedNotificationsEmail() throws JsonProcessingException;

    void processFailedNotificationsLetter() throws JsonProcessingException;

    NotificationTemplatePreviewResponse previewNotification(DocPreviewRequest docPreviewRequest, MultiValueMap<String, String> headers);

}
