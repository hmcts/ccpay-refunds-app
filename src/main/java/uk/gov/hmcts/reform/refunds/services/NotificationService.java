package uk.gov.hmcts.reform.refunds.services;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.TemplatePreview;
import uk.gov.hmcts.reform.refunds.dtos.responses.Notification;
import uk.gov.hmcts.reform.refunds.model.Refund;

public interface NotificationService {

    ResponseEntity<String> postEmailNotificationData(MultiValueMap<String, String> headers,
                                                     RefundNotificationEmailRequest refundNotificationEmailRequest);

    ResponseEntity<String> postLetterNotificationData(MultiValueMap<String, String> headers,
                                                      RefundNotificationLetterRequest refundNotificationLetterRequest);

    void updateNotification(MultiValueMap<String, String> headers, Refund refund, TemplatePreview templatePreview);

    void updateNotification(MultiValueMap<String, String> headers, Refund refund, TemplatePreview templatePreview, String templateId);

    void updateNotification(MultiValueMap<String, String> headers, Refund refund, String templateId);

    Notification getNotificationDetails(MultiValueMap<String, String> headers, String reference);
}
