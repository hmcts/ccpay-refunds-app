package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.Personalisation;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.model.Refund;

@Component
public class RefundNotificationMapper {

    @Value("${notification.email-to-reply}")
    private String emailReplyToId;

    @Value("${notification.service-mailbox}")
    private String serviceMailBox;

    @Value("${notification.service-url}")
    private String serviceUrl;



    public RefundNotificationEmailRequest getRefundNotificationEmailRequest(Refund refund, ResendNotificationRequest resendNotificationRequest) {
        return RefundNotificationEmailRequest.refundNotificationEmailRequestWith()
                .templateId(resendNotificationRequest.getTemplateId())
                .recipientEmailAddress(resendNotificationRequest.getRecipientEmailAddress())
                .reference(resendNotificationRequest.getReference())
                .emailReplyToId(emailReplyToId)
                .notificationType(NotificationType.EMAIL)
                .personalisation(Personalisation.personalisationRequestWith()
                                     .ccdCaseNumber(refund.getCcdCaseNumber())
                                     .refundReference(refund.getReference())
                                     .serviceMailBox(serviceMailBox)
                                     .serviceUrl(serviceUrl)
                                     .build())
                .build();
    }

    public RefundNotificationLetterRequest getRefundNotificationLetterRequest(Refund refund, ResendNotificationRequest resendNotificationRequest) {
        return RefundNotificationLetterRequest.refundNotificationLetterRequestWith()
            .templateId(resendNotificationRequest.getTemplateId())
            .recipientPostalAddress(resendNotificationRequest.getRecipientPostalAddress())
            .reference(resendNotificationRequest.getReference())
            .notificationType(NotificationType.EMAIL)
            .personalisation(Personalisation.personalisationRequestWith()
                                 .ccdCaseNumber(refund.getCcdCaseNumber())
                                 .refundReference(refund.getReference())
                                 .serviceMailBox(serviceMailBox)
                                 .serviceUrl(serviceUrl)
                                 .build())
            .build();
    }
}
