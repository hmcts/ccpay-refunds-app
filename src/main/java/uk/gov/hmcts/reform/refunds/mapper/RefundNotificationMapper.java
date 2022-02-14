package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.Personalisation;
import uk.gov.hmcts.reform.refunds.dtos.requests.RecipientPostalAddress;
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

    @Value("${notify.template.cheque-po-cash.letter}")
    private String chequePoCashLetterTemplateId;

    @Value("${notify.template.cheque-po-cash.email}")
    private String chequePoCashEmailTemplateId;

    @Value("${notify.template.card-pba.letter}")
    private String cardPbaLetterTemplateId;

    @Value("${notify.template.card-pba.email}")
    private String cardPbaEmailTemplateId;

    public RefundNotificationEmailRequest getRefundNotificationEmailRequest(Refund refund,
                                                                            ResendNotificationRequest resendNotificationRequest) {
        return RefundNotificationEmailRequest.refundNotificationEmailRequestWith()
                .templateId(refund.getContactDetails().getTemplateId())
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

    public RefundNotificationLetterRequest getRefundNotificationLetterRequest(Refund refund,
                                                                              ResendNotificationRequest resendNotificationRequest) {
        return RefundNotificationLetterRequest.refundNotificationLetterRequestWith()
            .templateId(refund.getContactDetails().getTemplateId())
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

    public RefundNotificationEmailRequest getRefundNotificationEmailRequestApproveJourney(Refund refund) {
        return RefundNotificationEmailRequest.refundNotificationEmailRequestWith()
            .templateId(chequePoCashEmailTemplateId)
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

    public RefundNotificationLetterRequest getRefundNotificationLetterRequestApproveJourney(Refund refund) {
        RecipientPostalAddress recipientPostalAddress = new RecipientPostalAddress();
        recipientPostalAddress.setAddressLine(refund.getContactDetails().getAddressLine());
        recipientPostalAddress.setPostalCode(refund.getContactDetails().getPostalCode());
        recipientPostalAddress.setCity(refund.getContactDetails().getCity());
        recipientPostalAddress.setCountry(refund.getContactDetails().getCountry());
        return RefundNotificationLetterRequest.refundNotificationLetterRequestWith()
            .templateId(cardPbaLetterTemplateId)
            .recipientPostalAddress(recipientPostalAddress)
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
