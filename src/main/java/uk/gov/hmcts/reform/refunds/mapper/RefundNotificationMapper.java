package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.*;
import uk.gov.hmcts.reform.refunds.model.Refund;

@Component
public class RefundNotificationMapper {

    @Value("${notification.email-to-reply}")
    private String emailReplyToId;

    @Value("${notification.service-mailbox}")
    private String serviceMailBox;

    @Value("${notification.service-url}")
    private String serviceUrl;

    @Value("${notify.letter.template}")
    private String letterTemplateId;

    @Value("${notify.email.template}")
    private String emailTemplateId;



    public RefundNotificationEmailRequest getRefundNotificationEmailRequest(Refund refund, ResendNotificationRequest resendNotificationRequest) {
        return RefundNotificationEmailRequest.refundNotificationEmailRequestWith()
                .templateId(emailTemplateId)
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
            .templateId(letterTemplateId)
            .recipientPostalAddress(resendNotificationRequest.getRecipientPostalAddress())
            .reference(resendNotificationRequest.getReference())
            .notificationType(NotificationType.LETTER)
            .personalisation(Personalisation.personalisationRequestWith()
                                 .ccdCaseNumber(refund.getCcdCaseNumber())
                                 .refundReference(refund.getReference())
                                 .serviceMailBox(serviceMailBox)
                                 .serviceUrl(serviceUrl)
                                 .build())
            .build();
    }

    public RefundNotificationEmailRequest getRefundNotificationEmailRequest(Refund refund) {
        return RefundNotificationEmailRequest.refundNotificationEmailRequestWith()
            .templateId(emailTemplateId)
            .recipientEmailAddress(refund.getContactDetails().getEmail())
            .reference(refund.getReference())
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

    public RefundNotificationLetterRequest getRefundNotificationLetterRequest(Refund refund) {
        return RefundNotificationLetterRequest.refundNotificationLetterRequestWith()
            .templateId(letterTemplateId)
            .recipientPostalAddress(RecipientPostalAddress.recipientPostalAddressWith()
                                        .addressLine(refund.getContactDetails().getAddressLine())
                                        .city(refund.getContactDetails().getCity())
                                        .county(refund.getContactDetails().getCounty())
                                        .country(refund.getContactDetails().getCountry())
                                        .postalCode(refund.getContactDetails().getPostalCode())
                                        .build())
            .reference(refund.getReference())
            .notificationType(NotificationType.LETTER)
            .personalisation(Personalisation.personalisationRequestWith()
                                 .ccdCaseNumber(refund.getCcdCaseNumber())
                                 .refundReference(refund.getReference())
                                 .serviceMailBox(serviceMailBox)
                                 .serviceUrl(serviceUrl)
                                 .build())
            .build();
    }
}
