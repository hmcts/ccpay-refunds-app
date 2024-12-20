package uk.gov.hmcts.reform.refunds.mapper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.Personalisation;
import uk.gov.hmcts.reform.refunds.dtos.requests.RecipientPostalAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.TemplatePreview;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;

@Component
public class RefundNotificationMapper {

    @Value("${notification.email-to-reply}")
    private String emailReplyToId;

    @Autowired
    RefundsUtil refundsUtil;

    public RefundNotificationEmailRequest getRefundNotificationEmailRequest(Refund refund,
                                                                            ResendNotificationRequest resendNotificationRequest, String customerReference) {
        return RefundNotificationEmailRequest.refundNotificationEmailRequestWith()
                .templateId(refundsUtil.getTemplate(refund))
                .recipientEmailAddress(resendNotificationRequest.getRecipientEmailAddress())
                .reference(resendNotificationRequest.getReference())
                .emailReplyToId(emailReplyToId)
                .notificationType(NotificationType.EMAIL)
                .personalisation(Personalisation.personalisationRequestWith()
                                     .ccdCaseNumber(refund.getCcdCaseNumber())
                                     .refundReference(refund.getReference())
                                     .refundAmount(refund.getAmount())
                                     .refundReason(refund.getReason())
                                     .customerReference(customerReference)
                                     .build())
            .serviceName(refund.getServiceType())
            .build();
    }

    public RefundNotificationLetterRequest getRefundNotificationLetterRequest(Refund refund,
                                                                              ResendNotificationRequest resendNotificationRequest, String customerReference) {
        return RefundNotificationLetterRequest.refundNotificationLetterRequestWith()
            .templateId(refundsUtil.getTemplate(refund))
            .recipientPostalAddress(resendNotificationRequest.getRecipientPostalAddress())
            .reference(resendNotificationRequest.getReference())
            .notificationType(NotificationType.LETTER)
            .personalisation(Personalisation.personalisationRequestWith()
                                 .ccdCaseNumber(refund.getCcdCaseNumber())
                                 .refundReference(refund.getReference())
                                 .refundAmount(refund.getAmount())
                                 .refundReason(refund.getReason())
                                 .customerReference(customerReference)
                                 .build())
            .serviceName(refund.getServiceType())
            .build();
    }

    public RefundNotificationEmailRequest getRefundNotificationEmailRequestApproveJourney(
        Refund refund, TemplatePreview templatePreview, String templateId, String customerReference) {

        RefundNotificationEmailRequest request = getRefundNotificationEmailRequestApproveJourney(refund, customerReference);

        if (!StringUtils.isEmpty(templateId)) {
            request.setTemplateId(templateId);
        }

        if (templatePreview != null) {
            request.setTemplatePreview(TemplatePreview.templatePreviewWith()
                                           .id(templatePreview.getId())
                                           .subject(templatePreview.getSubject())
                                           .templateType(templatePreview.getTemplateType())
                                           .version(templatePreview.getVersion())
                                           .body(templatePreview.getBody())
                                           .html(templatePreview.getHtml())
                                           .from(templatePreview.getFrom())
                                           .build());
        }
        return request;
    }

    public RefundNotificationEmailRequest getRefundNotificationEmailRequestApproveJourney(Refund refund, String customerReference) {
        return RefundNotificationEmailRequest.refundNotificationEmailRequestWith()
            .templateId(refundsUtil.getTemplate(refund))
            .recipientEmailAddress(refund.getContactDetails().getEmail())
            .reference(refund.getReference())
            .emailReplyToId(emailReplyToId)
            .notificationType(NotificationType.EMAIL)
            .personalisation(Personalisation.personalisationRequestWith()
                                 .ccdCaseNumber(refund.getCcdCaseNumber())
                                 .refundReference(refund.getReference())
                                 .refundAmount(refund.getAmount())
                                 .refundReason(refund.getReason())
                                 .customerReference(customerReference)
                                 .build())
            .serviceName(refund.getServiceType())
            .build();
    }

    public RefundNotificationLetterRequest getRefundNotificationLetterRequestApproveJourney(
        Refund refund, TemplatePreview templatePreview, String templateId, String customerReference) {

        RefundNotificationLetterRequest request = getRefundNotificationLetterRequestApproveJourney(refund, customerReference);

        if (!StringUtils.isEmpty(templateId)) {
            request.setTemplateId(templateId);
        }

        if (templatePreview != null) {
            request.setTemplatePreview(TemplatePreview.templatePreviewWith()
                                           .id(templatePreview.getId())
                                           .subject(templatePreview.getSubject())
                                           .templateType(templatePreview.getTemplateType())
                                           .version(templatePreview.getVersion())
                                           .body(templatePreview.getBody())
                                           .html(templatePreview.getHtml())
                                           .from(templatePreview.getFrom())
                                           .build()
            );
        }
        return request;
    }

    public RefundNotificationLetterRequest getRefundNotificationLetterRequestApproveJourney(Refund refund, String customerReference) {
        RecipientPostalAddress recipientPostalAddress = new RecipientPostalAddress();
        recipientPostalAddress.setAddressLine(refund.getContactDetails().getAddressLine());
        recipientPostalAddress.setPostalCode(refund.getContactDetails().getPostalCode());
        recipientPostalAddress.setCity(refund.getContactDetails().getCity());
        recipientPostalAddress.setCountry(refund.getContactDetails().getCountry());
        recipientPostalAddress.setCounty(refund.getContactDetails().getCounty());
        return RefundNotificationLetterRequest.refundNotificationLetterRequestWith()
            .templateId(refundsUtil.getTemplate(refund))
            .recipientPostalAddress(recipientPostalAddress)
            .reference(refund.getReference())
            .notificationType(NotificationType.LETTER)
            .personalisation(Personalisation.personalisationRequestWith()
                                 .ccdCaseNumber(refund.getCcdCaseNumber())
                                 .refundReference(refund.getReference())
                                 .refundAmount(refund.getAmount())
                                 .refundReason(refund.getReason())
                                 .customerReference(customerReference)
                                 .build())
            .serviceName(refund.getServiceType())
            .build();
    }

}
