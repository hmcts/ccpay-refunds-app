package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;

public class RefundNotificationServiceImpl implements RefundNotificationService{


    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RefundNotificationMapper refundNotificationMapper;

    @Autowired
    private RefundsService refundsService;

    @Autowired
    private RefundsRepository refundsRepository;

    @Override
    public ResponseEntity resendRefundNotification(Boolean resendToNewContactDetail, ResendNotificationRequest resendNotificationRequest, MultiValueMap<String, String> headers) {
        NotificationType notificationType = resendNotificationRequest.getNotificationType();
        Refund refund = refundsService.getRefundForReference(resendNotificationRequest.getReference());

        ResponseEntity responseEntity;
        if(notificationType.equals(NotificationType.EMAIL)){
            if(resendToNewContactDetail){
                ContactDetails newContact = ContactDetails.contactDetailsWith()
                                             .email(resendNotificationRequest.getRecipientEmailAddress())
                                             .templateId(resendNotificationRequest.getTemplateId())
                                             .notificationType(NotificationType.EMAIL.name())
                                             .build();
                refund.setContactDetails(newContact);
                refund.setNotificationSentFlag("email_not_sent");
            }
            RefundNotificationEmailRequest refundNotificationEmailRequest = refundNotificationMapper
                .getRefundNotificationEmailRequest(refund, resendNotificationRequest);
            responseEntity = notificationService.postEmailNotificationData(headers,refundNotificationEmailRequest);
        } else {
            if(resendToNewContactDetail){
                ContactDetails newContact = ContactDetails.contactDetailsWith()
                    .templateId(resendNotificationRequest.getTemplateId())
                    .addressLine(resendNotificationRequest.getRecipientPostalAddress().getAddressLine())
                    .county(resendNotificationRequest.getRecipientPostalAddress().getCounty())
                    .postalCode(resendNotificationRequest.getRecipientPostalAddress().getPostalCode())
                    .city(resendNotificationRequest.getRecipientPostalAddress().getCity())
                    .country(resendNotificationRequest.getRecipientPostalAddress().getCountry())
                    .notificationType(NotificationType.LETTER.name())
                    .build();
                refund.setContactDetails(newContact);
                refund.setNotificationSentFlag("letter_not_sent");
            }
            RefundNotificationLetterRequest refundNotificationLetterRequestRequest = refundNotificationMapper.getRefundNotificationLetterRequest(refund, resendNotificationRequest);
            responseEntity = notificationService.postLetterNotificationData(headers,refundNotificationLetterRequestRequest);

        }
        if(responseEntity.getStatusCode().is2xxSuccessful()){
            refund.setNotificationSentFlag("sent");
            refund.setContactDetails(null);
        }
        refundsRepository.save(refund);

        return responseEntity;
    }
}
